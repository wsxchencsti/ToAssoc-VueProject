import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.HashPartitioner
import scala.collection.mutable
import org.apache.log4j.{Logger, Level}
import scala.collection.mutable.{HashSet, HashMap}

// 基础类定义
class VertexData(val vId: Long, var cId: Long) extends Serializable {
  var innerDegree   = 0.0
  var innerVertices = new mutable.HashSet[Long]()
  var degree        = 0.0
}

case class LouvainConfig(
  maxIter: Int,
  internalIter: Int, 
  tol: Double,
  alpha: Double,
  dateParam: String
)

// 工具函数
def toBase32(value: Long): String = {
  java.lang.Long.toString(value, 32).toUpperCase()
}

// 日志记录
val LOGGER = Logger.getLogger("Louvain")

// 2. LouvainGraphUtil对象
object LouvainGraphUtil {
  /**
   * Construct louvain graph
   *
   * @param initG
   * @return Graph
   */
  def createLouvainGraph(
                          initG: Graph[None.type, Double]
                        ): Graph[VertexData, Double] = {
    // sum of the weights of the links incident to node i
    // node i 内部的度求和
    // 计算了每个节点的邻居边的权重之和，并将结果作为节点属性进行保存
    val nodeWeights: VertexRDD[Double] = initG.aggregateMessages(
      // EdgeContext[VD, ED, A]的参数，其中VD是顶点属性的类型，ED是边属性的类型，A是投递的消息类型。
      // 每个节点将接收到其邻居节点的边的权重作为消息
      (trip: EdgeContext[None.type, Double, Double]) => {
        trip.sendToSrc(trip.attr)
        trip.sendToDst(trip.attr)
      },
      (a, b) => a + b
    )
    // update graph vertex's property
    // 更新图中点的属性
    val louvainG: Graph[VertexData, Double] = initG.outerJoinVertices(nodeWeights)((vid, oldData, opt) => {
      val vData   = new VertexData(vid, vid)
      val weights = opt.getOrElse(0.0)
      // 每个节点将接收到其邻居节点的边的权重作为消息
      vData.degree = weights
      // 社群内增加点
      vData.innerVertices += vid
      vData
    })
    louvainG
  }

  /**
   * 网络收缩的时候调用
   * update graph using new community info
   *
   * @param G          Louvain graph
   * @param changeInfo （vid，new_cid）
   *
   * @return Graph[VertexData, Double]
   */
  def updateGraph(
                   G: Graph[VertexData, Double],
                   changeInfo: RDD[(VertexId, Long)]  // 点ID/社群ID  所属连通分量ID
                 ): Graph[VertexData, Double] = {
    // update community id
    G.joinVertices(changeInfo)((vid, data, newCid) => {
      val vData = new VertexData(vid, newCid)
      vData.innerDegree = data.innerDegree
      vData.innerVertices = data.innerVertices
      vData.degree = data.degree
      vData
    })
  }
}

// 3. CommUtil对象  
object CommUtil extends Serializable {
  def getCommunities(G: Graph[VertexData, Double], louvainConfig: LouvainConfig, spark: SparkSession): RDD[Row] = {
    // group_id   点ID
    val resultRDD: RDD[(VertexId, VertexId)] = G.vertices
      .flatMapValues(_.innerVertices)

    // 将louvainConfig提取为局部变量，避免捕获对象引用
    val dateParam = louvainConfig.dateParam
    
    val communities: RDD[Row] = resultRDD
      .mapPartitions(iter => iter.map { case (communityId, paperId) =>
        if (communityId == 0L)
          Row(paperId, dateParam + "_" + "W")
        else
          Row(paperId, dateParam + "_" + toBase32(communityId))
      })
    communities    
  }
}

// 4. 主Louvain对象
object Louvain {
  def execute(spark: SparkSession,
              graph: Graph[None.type, Double],
              louvainConfig: LouvainConfig): RDD[Row] = {

    val sc = spark.sparkContext

    // convert origin graph to Louvain Graph, Louvain Graph records vertex's community、innerVertices and innerDegrees
    // louvainG: 点的社群or社群内有多少点or内部的度
    var louvainG: Graph[VertexData, Double] = LouvainGraphUtil.createLouvainGraph(graph)


    // compute and broadcast the sum of all edges
    // 计算图中所有边的权重和，并进行广播，记为m
    val m = sc.broadcast(louvainG.edges.mapPartitions(iter => iter.map(value => value.attr)).sum())
    var curIter = 0
    var flag = true
    // step1:对应一中的局部优化
    // step2：对应一中的网络收缩
    // 完成一次step1和step2视作完成一次循环
    while (flag && curIter < louvainConfig.maxIter) {
      val res = step1(louvainConfig.internalIter, louvainG, m.value, louvainConfig.tol, louvainConfig.alpha)
      louvainG = res._1
      louvainG = step2(louvainG)
      curIter += 1
      // 判断 是否已经迭代到收敛
      if (res._2)
        flag = false
    }
    // 获取最终的结果，两列数据，左列点ID  右列点所属社群ID
    CommUtil.getCommunities(louvainG, louvainConfig, spark)
  }
  // 这里粘贴step1方法
  
  /**
   * Louvain step1：Traverse the vertices and get the new community information of each node遍历节点，获取每个节点对应的所属新社区信息
   *
   *   1. Calculate the information of the community that each vertex currently belongs to.
   *      2. Calculate the community modularity deference and get the community info which has max modularity deference.
   *      3. Count the number of vertices that have changed in the community, used to determine whether the internal iteration can stop.
   *      4. Update vertices' community id and update each community's innerVertices.
   *
   * This step update vertexData's cid and commVertex.
   *
   * @param maxIter max interation
   * @param louvainG
   * @param m       The sum of the weights of all edges in the graph
   * @return (Graph[VertexData,Double],Int)
   */

  // This step update vertexData's cid and commVertex.
  // Louvain步骤1：遍历顶点并获得每个节点的新社区信息
  //*1。计算每个顶点当前所属社区的信息。
  //*2。计算社区模块性差异，得到模块性差异最大的社区信息。
  //*3。计算社区中已更改的顶点数，用于确定内部迭代是否可以停止。
  //*4。更新顶点的社区id并更新每个社区的innerVertices。
  def step1(
             internalIter: Int,
             louvainG: Graph[VertexData, Double],
             m: Double,
             tol: Double,
             alpha: Double
           ): (Graph[VertexData, Double], Boolean) = {
    LOGGER.info("============================== step 1 =======================")
    var G = louvainG
    var canStop = false
    var iterTime = 0
    while (iterTime < internalIter && !canStop) {
      val neighborComm: RDD[(VertexId, Iterable[(VertexId, Double, Double)])] = getNeighCommInfo(G)
      // 点ID  社群ID
      val changeInfo: RDD[(VertexId, VertexId, Double)] = getChangeInfo(G, neighborComm, m, tol, alpha)
      val changeCount: VertexId = changeInfo.filter(_._3 != 0.0).count()
      // 如果在一次迭代中没有任何一个点所属的社区发生变化，那么迭代停止
      // canStop 设置为true
      if (changeCount == 0L) {
        canStop = true
      } else {
        // 点ID/社群ID  所属连通分量ID
        val newChangeInfo: RDD[(VertexId, Long)] = Graph
          .fromEdgeTuples(changeInfo.map((x: (VertexId, VertexId, Double)) => (x._1, x._2)), 0)
          .connectedComponents()
          .vertices
        G = LouvainGraphUtil.updateGraph(G, newChangeInfo)
        // iterTime 总计迭代次数
        iterTime += 1
      }
    }
    (G, canStop)
  }
  
  // step1 工具方法
  /**
   * get new community's basic info after the vertex joins the community
   *   1. get each vertex's community id and the community's tot.
   *   2. compute each vertex's k_in. (The sum of the edge weights between vertex and vertex i in the community)
   *
   * @param G
   */
  //在顶点加入社区后获取新社区的基本信息
  //获取每个顶点的社区id和社区的tot
  //计算每个顶点的k_in （社区中顶点和顶点i之间的边权重之和) 
                        G: Graph[VertexData, Double]
                      ): RDD[(VertexId, Iterable[(Long, Double, Double)])] = {

    val commKIn: RDD[(VertexId, (Double, mutable.HashMap[VertexId, Double]))] = G.triplets
      .repartition(100)
      .flatMap((trip: EdgeTriplet[VertexData, Double]) => {
        Array( //(VertexId, ((VertexId, Double), (VertexId, Double)))
          // 自己的社群ID   (相邻点, 边权重)   (自己的点ID，自己的社群内部边的权重和 + 自己的 每个节点将接收到其邻居节点的边的权重作为消息)
          (
            trip.srcAttr.cId, // src点的社群ID
            (
              (trip.dstId -> trip.attr),
              (trip.srcId, trip.srcAttr.innerDegree + trip.srcAttr.degree) // 社区的tot 它表示整个社区内节点与社区内外的边的总数
            )
          ),
          (
            trip.dstAttr.cId, // dst点的社群ID
            (
              (trip.srcId -> trip.attr),
              (trip.dstId, trip.dstAttr.innerDegree + trip.dstAttr.degree)
            )
          )
        )
      })
      //      .groupByKey() // 按社群ID分组
      .combineByKey(
        value => Seq(value),
        (seq: Seq[((VertexId, Double), (VertexId, Double))], value) => seq :+ value,
        (seq1: Seq[((VertexId, Double), (VertexId, Double))], seq2: Seq[((VertexId, Double), (VertexId, Double))]) => seq1 ++ seq2
      )
      // (相邻点, 边权重)   (自己的点ID，自己的社群内部边的权重和 + 自己的 每个节点将接收到其邻居节点的边的权重作为消息)
      .map((t: (VertexId, Iterable[((VertexId, Double), (VertexId, Double))])) => {
        val cid = t._1 // 社群ID
        // add the weight of the same vid in one community.
        // 计算每个顶点的k_in （社区中顶点和顶点i之间的边权重之和) k_in代表节点在其所属社区内部的度（即内部度）
        val m = new HashMap[VertexId, Double]() // store community's vertexId and vertex kin 存每个社群内部的点 以及对应的点的k_in
        val degrees = new HashSet[VertexId]() // record if all vertices has computed the tot
        var tot = 0.0
        for (x <- t._2) {
          if (m.contains(x._1._1))
            m(x._1._1) += x._1._2
          else
            m(x._1._1) = x._1._2
          // compute vertex's tot
          if (!degrees.contains(x._2._1)) {
            tot += x._2._2
            degrees += x._2._1
          }
        }
        (cid, (tot, m))
      }) // 社群ID (tot, m) 存每个社群内部的点ID 以及对应的点的k_in

    // convert commKIn
    val neighCommInfo: RDD[(VertexId, Iterable[(VertexId, Double, Double)])] = commKIn
      .flatMap((x: (VertexId, (Double, mutable.HashMap[VertexId, Double]))) => {
        val cid: VertexId = x._1
        val tot: Double = x._2._1
        x._2._2.map((t: (VertexId, Double)) => {
          val vid: VertexId = t._1
          val kIn: Double = t._2
          (vid, (cid, kIn, tot)) // 点ID  点所属社群ID  kin tot
        })
      })
      //      .groupByKey()
      .combineByKey(
        (value: (VertexId, Double, Double)) => Iterable(value),
        (seq: Iterable[(VertexId, Double, Double)], value) => seq.toSeq :+ value,
        (seq1: Iterable[(VertexId, Double, Double)], seq2: Iterable[(VertexId, Double, Double)]) => seq1 ++ seq2
      )
    neighCommInfo
  }

  /**
   * Calculate the influence of each vertex on the modularity change of neighbor communities, and find the most suitable community for the vertex
   * 计算每个顶点对相邻社区模块化变化的影响，找到最适合该顶点的社区
   *
   * @param G             graph
   * @param neighCommInfo neighbor community info
   * @param m             broadcast value
   * @param tol           threshold for modularity deference
   * @return RDD
   */
  def getChangeInfo(G: Graph[VertexData, Double],
                    neighCommInfo: RDD[(VertexId, Iterable[(Long, Double, Double)])],
                    m: Double,
                    tol: Double,
                    alpha: Double): RDD[(VertexId, Long, Double)] = {
    val numPartitions: Int = G.vertices.getNumPartitions
    val value: RDD[(VertexId, VertexData)] = G.vertices.partitionBy(new HashPartitioner(numPartitions))
    val value1: RDD[(VertexId, Iterable[(VertexId, Double, Double)])] = neighCommInfo.partitionBy(new HashPartitioner(numPartitions))
    val changeInfo = value
      .join(value1)
      .mapPartitions(iter => {
        iter.map(x => {
          val vid: VertexId = x._1
          val data: VertexData = x._2._1
          /**
           * class VertexData(val vId: Long, var cId: Long) extends Serializable {
           * // 社群内部边的权重和
           * var innerDegree   = 0.0
           * // 社群内的点的集合
           * var innerVertices = new mutable.HashSet[Long]()
           * // 度  每个节点将接收到其邻居节点的边的权重作为消息
           * var degree        = 0.0
           * }
           */
          val commIter: Iterable[(VertexId, Double, Double)] = x._2._2
          val vCid: VertexId = data.cId
          val k_v: Double = data.degree + data.innerDegree
          val vertices: mutable.HashSet[VertexId] = data.innerVertices

          val dertaQs = commIter.map(t => {
            val nCid: VertexId = t._1 // neighbor community id
            val k_v_in: Double = t._2
            var tot: Double = t._3
            var q = alpha * k_v_in - (k_v * tot) / (2.0 * m)
            if (vCid == nCid && vertices.size > 1) {
              q = alpha * k_v_in  -  ((tot - k_v) * k_v) / (2.0 * m)
            }
            if (vCid == nCid && vertices.size == 1)
              q = 0.0
            (vid, nCid, q)
          })
          val maxQ: (VertexId, VertexId, Double) =
            dertaQs.max(Ordering.by[(VertexId, Long, Double), Double](_._3))
          if (maxQ._3 > tol)
            maxQ
          else // if entering other communities reduces its modularity, then stays in the current community
            (vid, vCid, 0.0)
        })
      })

    changeInfo //(vid,wCid,△Q)
  }
  
  /**
   * Louvain step 2：Combine the new graph node obtained in the first step into a super node according to
   * the community information to which it belongs.
   *
   * Louvain步骤2：将第一步中获得的新图节点根据其所属的社区信息合并为超级节点。
   *
   * @param G graph
   * @return graph
   */
  def step2(G: Graph[VertexData, Double]): Graph[VertexData, Double] = {
    LOGGER.info("============================== step 2 =======================")
    //get edges between different communities
    // 社区内节点之间的边的权重转化为新节点的环的权重，社区间的边权重转化为新节点间的边权重；
    val edges: RDD[Edge[Double]] = G.triplets
      //       .filter(trip => trip.srcAttr.cId != trip.dstAttr.cId)
      .mapPartitions(iter => {
        iter.map(trip => {
          val cid1 = trip.srcAttr.cId
          val cid2 = trip.dstAttr.cId
          val weight = trip.attr
          ((math.min(cid1, cid2), math.max(cid1, cid2)), weight)
        })
      })
      .reduceByKey(_ + _)
      //.map(t => (t._1._1, t._1._2, t._2))
      .mapPartitions(iter => iter.map(x => Edge(x._1._1, x._1._2, x._2))) //sum the edge weights between communities
    // 社区间的权重包含了 社区内部的权重和社区间的权重
    //计算每个社区内部边的权重


    // sum kin of all vertices within the same community
    // k_in代表节点在其所属社区内部的度（即内部度）。内部度是指节点与同一个社区内其他节点之间的边的数量。
    val vInnerKin: RDD[(VertexId, (Set[VertexId], Double))] = G.vertices
      .mapPartitions(iter => iter.map(v => (v._2.cId, (v._2.innerVertices.toSet, v._2.innerDegree))))
      //      .map(v => (v._2.cId, (v._2.innerVertices.toSet, v._2.innerDegree)))
      .reduceByKey((x, y) => {
        val vertices = (x._1 ++ y._1)
        val kIn = x._2 + y._2
        (vertices, kIn)
      })

    // new super vertex
    val superVertexInfo: RDD[(VertexId, (Set[VertexId], Double))] = vInnerKin
      .reduceByKey((x, y) => {
        val vertices = x._1 ++ y._1
        val kIn = x._2 + y._2
        (vertices, kIn)
      })

    // reconstruct graph based on new edge info
    val initG: Graph[None.type, Double] = Graph.fromEdges(edges, None)
    var louvainGraph: Graph[VertexData, Double] = LouvainGraphUtil.createLouvainGraph(initG)


    // get new louvain graph
    // 用superVertexInfo初始化louvainGraph
    louvainGraph = louvainGraph.joinVertices(superVertexInfo)((vid, data, opt) => {
      var innerVerteices = new HashSet[VertexId]()
      val kIn: Double = opt._2
      for (vid <- opt._1)
        innerVerteices += vid
      data.innerVertices = innerVerteices
      data.innerDegree = kIn
      data
    })
    louvainGraph
  }
}


