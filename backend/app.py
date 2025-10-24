#  uvicorn app:app --reload --host 0.0.0.0 --port 8000
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
import psycopg2
from sentence_transformers import SentenceTransformer
from os import getenv
from dotenv import load_dotenv
import numpy as np
import ast


print("### Backend Start... ###")
print("### Loading Env... ###")

# loading env
load_dotenv()
DB_NAME = getenv('DB_NAME')
DB_USER = getenv('DB_USER')
DB_PWD = getenv('DB_PASSWORD')
DB_PORT = getenv('DB_PORT')
print("### [OK1/4]Env Loaded! ###")
print("### Connecting Database... ###")

# connect database
conn = psycopg2.connect(
    dbname=DB_NAME,
    user=DB_USER,
    password=DB_PWD,
    host="localhost",
    port=DB_PORT,
)
cur = conn.cursor()
print("### [OK2/4]Database Connected! ###")

# load model
print("### Loading BGEModel... ###")
model = SentenceTransformer('BAAI/bge-base-en-v1.5')
print("### [OK3/4]BGEModel Loaded! ###")

# api
print("### Initializing API... ###")
app = FastAPI()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"]
)
print("### [OK4/4]API Initialized! ###")
print('### Ready to Accept Requests ###')
print('----------- Waiting -----------')


@app.get("/search")
def search(query: str = Query(..., min_length=1)):
    print(f'--[New]Get New Request: {query}--')

    print('--[1/5]Making Embeddings--')
    query_embedding = model.encode(query, normalize_embeddings=True).astype('float32')
    vec_str = "[" + ",".join(map(str, query_embedding.tolist())) + "]"
    # use ivfflat
    cur.execute("SET enable_seqscan = off;")
    cur.execute("SET ivfflat.probes = 100;")
    print('--[1/5]Done.--')

    # search topk
    print('--[2/5]Search for Top-K--')
    cur.execute(f"""
        SELECT p.id, p.arxiv_id, p.title, p.abstract, p.categories, p.update_date,
               p.doi, p.authors, p.comments, p.journal_ref,
               p.citation_count, p.embedding_vector,
               p.embedding_vector <=> '{vec_str}'::vector AS similarity,
               pc.community_id
        FROM papers p
        LEFT JOIN paper_communities pc ON p.id = pc.paper_id 
        WHERE p.embedding_vector IS NOT NULL
        ORDER BY p.embedding_vector <=> '{vec_str}'::vector ASC
        LIMIT 100;
    """)
    results = cur.fetchall()
    print('--[2/5]Done.--')

    # to json and embeeding
    print('--[3/5]To Json--')
    nodes = []
    embeddings = []
    for r in results:
        (
            paper_id,
            arxiv_id,
            title,
            abstract,
            categories,
            update_date,
            doi,
            authors,
            comments,
            journal_ref,
            citation_count,
            embedding_vector,
            similarity,
            community_id
        ) = r

        nodes.append({
            "id": str(paper_id),
            "arxiv_id": arxiv_id,
            "title": title,
            "abstract": abstract,
            "categories": categories,
            "update_date": update_date.isoformat() if update_date else None,
            "doi": doi,
            "authors": authors,
            "comments": comments,
            "journal_ref": journal_ref,
            "citation_count": citation_count,
            "relevance": 1 - float(similarity),
            "community_id": community_id
        })

        embeddings.append(np.array(ast.literal_eval(embedding_vector), dtype=np.float32))

    embeddings = np.vstack(embeddings)  # (100, d)
    print('--[3/5]Done.--')
    print('--[4/5]Calculating Similarity--')
    sim_matrix = np.dot(embeddings, embeddings.T)  # (100,100)

    # calc similarity martix
    links = []
    for i in range(len(nodes)):
        sims = sim_matrix[i]
        top_k_idx = sims.argsort()[-6:-1][::-1]
        for j in top_k_idx:
            links.append({
                "source": nodes[i]["id"],
                "target": nodes[j]["id"],
                "weight": float(sims[j])
            })
    print('--[4/5]Done.--')
    print('--[5/5]Return Results--')
    print('--[5/5]Done.--')
    print('----------- Waiting -----------')
    return {
        "query": query,
        "nodes": nodes,
        "links": links
    }
    