-- Extensions used by later migrations:
--   pgcrypto: gen_random_uuid() for primary keys
--   vector:   embeddings for approved educational snippets (pgvector)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;
