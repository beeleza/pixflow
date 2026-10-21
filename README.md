# PixFlow

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![Kafka](https://img.shields.io/badge/Kafka-KRaft-black)
![Redis](https://img.shields.io/badge/Redis-7-red)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)

Serviço de transferências Pix com idempotência e liquidação assíncrona. A API registra a transferência
como `PENDING`, envia para a rede Pix via Kafka e, quando a resposta chega, marca a transação como
`COMPLETED` ou `FAILED`.

A rede Pix é simulada pelo [pixflow-network-simulator](https://github.com/beeleza/pixflow-network-simulator),
um serviço separado que consome as transferências, decide se são aceitas e publica o resultado da liquidação.

## Arquitetura

**C4 nível 1 — Contexto**

![C4 nível 1 - Contexto](docs/c4-nivel-1-contexto.png)

**C4 nível 2 — Containers**

![C4 nível 2 - Containers](docs/c4-nivel-2-containers.png)

- **Hexagonal (Ports & Adapters):** o domínio não depende de framework; os casos de uso só conhecem interfaces.
- **Redis:** lock por chave de idempotência (`SET NX` + TTL), impedindo processamento duplicado em paralelo.
- **Kafka:** envio em `pix-transaction` e retorno em `pix-transaction-response`.
- **pixflow-network-simulator:** faz o papel da rede Pix. Consome `pix-transaction`, valida a transferência
  e responde em `pix-transaction-response` com `accepted` e, se recusada, um `failureReason`
  (`INVALID_AMOUNT`, `INVALID_DESTINATION_KEY` ou `INVALID_SOURCE_ACCOUNT`).
- **PostgreSQL:** fonte da verdade; uma requisição repetida devolve a transação já existente.
  O schema é versionado com **Flyway** (`src/main/resources/db/migration`).
- **Optimistic locking:** cada transação tem uma coluna `version` (`@Version`), então duas liquidações
  concorrentes não sobrescrevem uma à outra; a que perde é reprocessada pelo retry do consumer.

## Como executar

Pré-requisito: Docker.

```bash
cp .env.example .env
docker compose up -d --build
```

Isso sobe a API, o simulador da rede Pix e a infraestrutura. A imagem do simulador é construída direto do
[repositório no GitHub](https://github.com/beeleza/pixflow-network-simulator). Para usar um clone local (por
exemplo, ao alterar o simulador), aponte `SIMULATOR_BUILD_CONTEXT` no `.env` para a pasta dele:

```bash
SIMULATOR_BUILD_CONTEXT=../pixflow-network-simulator
```

Para rodar a aplicação fora do Docker (requer Java 21), suba a infraestrutura e o simulador:

```bash
docker compose up -d postgres kafka redis network-simulator
./mvnw spring-boot:run
```

Sem o simulador rodando, as transferências ficam em `PENDING`, porque ninguém responde em
`pix-transaction-response`.

A API fica disponível em `http://localhost:8080`.

| Serviço  | Endereço                |
|----------|-------------------------|
| API      | `http://localhost:8080` |
| Kafka UI | `http://localhost:8090` |
| pgAdmin  | `http://localhost:5050` |

### Configuração

Todas as conexões aceitam variáveis de ambiente, com valores padrão para o ambiente local:

| Variável                  | Padrão                                     |
|---------------------------|--------------------------------------------|
| `SERVER_PORT`             | `8080`                                     |
| `DB_URL`                  | `jdbc:postgresql://localhost:5432/pixflow` |
| `DB_USERNAME`             | `pixflow`                                  |
| `DB_PASSWORD`             | `pixflow`                                  |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                           |
| `REDIS_HOST`              | `localhost`                                |
| `REDIS_PORT`              | `6379`                                     |

No `docker compose`, as variáveis ficam no `.env` (veja `.env.example`). Os endereços de banco, Kafka e
Redis do container `app` já apontam para os serviços do compose:

| Variável           | Padrão                                                 |
|--------------------|--------------------------------------------------------|
| `SERVER_PORT`      | `8080`                                                 |
| `DB_NAME`          | `pixflow`                                              |
| `DB_USERNAME`      | `pixflow`                                              |
| `DB_PASSWORD`      | `pixflow`                                              |
| `PGADMIN_EMAIL`    | `admin@pixflow.com`                                    |
| `PGADMIN_PASSWORD` | `admin`                                                |
| `JAVA_OPTS`        | `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError` |
| `SIMULATOR_BUILD_CONTEXT` | `https://github.com/beeleza/pixflow-network-simulator.git#main` |

## API

### Criar transferência

`POST /api/v1/pix`

```json
{
  "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "sourceAccountId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "destinationKey": "fulano@email.com",
  "destinationKeyType": "EMAIL",
  "amount": 150.50
}
```

`destinationKeyType`: `CPF`, `CNPJ`, `EMAIL`, `PHONE` ou `RANDOM`.

Retorna `201 Created` com a transação em status `PENDING`:

```json
{
  "id": "3f2a9c1e-7b4d-4e8a-9c2f-1a2b3c4d5e6f",
  "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "sourceAccount": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "destinationKey": "fulano@email.com",
  "destinationKeyType": "EMAIL",
  "amount": 150.50,
  "createdAt": "2026-10-21T14:30:00Z",
  "status": "PENDING",
  "settledAt": null,
  "failureReason": null
}
```

### Consultar transferência

`GET /api/v1/pix/{id}`

Retorna `200 OK` com a transação no mesmo formato acima. Como a liquidação é assíncrona, use este endpoint para
acompanhar a transferência até ela sair de `PENDING`: em `COMPLETED`, `settledAt` vem preenchido; em `FAILED`,
`failureReason` traz o motivo da recusa. Retorna `404` se a transação não existir.

### Listar transferências

`GET /api/v1/pix?sourceAccountId={uuid}&status={status}&page={page}&size={size}`

| Parâmetro         | Obrigatório | Padrão | Descrição                                       |
|-------------------|-------------|--------|-------------------------------------------------|
| `sourceAccountId` | sim         | —      | Conta de origem das transferências              |
| `status`          | não         | todos  | `PENDING`, `COMPLETED` ou `FAILED`              |
| `page`            | não         | `0`    | Página, começando em `0`                        |
| `size`            | não         | `20`   | Itens por página, entre `1` e `100`             |

Retorna `200 OK` com as transações ordenadas da mais recente para a mais antiga:

```json
{
  "items": [ { "id": "3f2a9c1e-7b4d-4e8a-9c2f-1a2b3c4d5e6f", "status": "COMPLETED", "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### Erros

Os erros seguem a [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/pix",
  "errors": [
    { "field": "amount", "message": "must be at least 0.01" }
  ]
}
```

| Status | Quando                                                                       |
|--------|------------------------------------------------------------------------------|
| `400`  | Campo ausente ou inválido, JSON malformado ou tipo de chave desconhecido     |
| `400`  | Parâmetro de consulta inválido (UUID malformado, `page` negativa, `size` fora de 1–100) |
| `404`  | Transação não encontrada                                                     |
| `409`  | Transferência com a mesma chave de idempotência ainda em processamento       |
| `422`  | Regra de negócio violada (ex.: chave Pix fora do formato do tipo informado) |
| `500`  | Erro inesperado                                                              |

## Testes

```bash
./mvnw test
```
