# CLAUDE.md — Regras para este repositório

## Schema do banco de dados

**Nunca altere o schema fora do Flyway, nem em desenvolvimento.**

Aplicar migração via `docker exec psql` cria um estado que nenhum outro ambiente terá e mascara falhas da própria migração. Qualquer alteração de schema (CREATE TABLE, ALTER TABLE, CREATE INDEX, DROP …) deve ser feita como arquivo `Vnn__descricao.sql` em `src/main/resources/db/migration/`.

Após aplicar uma migração ao banco compartilhado (dev ou produção), registre o checksum em `migration-checksums.txt` na mesma PR. Nunca edite um arquivo `Vnn` já listado nesse arquivo — crie uma nova migração para correções.
