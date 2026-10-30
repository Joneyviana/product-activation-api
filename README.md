# Product Activation API

API REST para registrar e consultar solicitações de ativação de produtos, com Transactional Outbox, processamento assíncrono e resiliência via anotações do Resilience4j.

## Stack

- Java 21, Spring Boot 3.5, Spring MVC e Spring Data JPA
- PostgreSQL 16 e Flyway
- Keycloak e OAuth2 Resource Server (JWT)
- Resilience4j `@Retry` + `@CircuitBreaker`
- JUnit 5, Mockito, Docker Compose e GitHub Actions

## Executar

Requisitos: Java 21, Maven e Docker.

```bash
docker compose up --build
```

A API estará em `http://localhost:8080`. O banco é inicializado pelo Compose e as migrations são aplicadas pelo Flyway.

Validar build e testes:

```bash
mvn verify
```

## Autenticação

A API está configurada como OAuth2 Resource Server e valida JWTs emitidos pelo Keycloak.

No ambiente local, considerando o mapeamento de portas `8000:8080` do serviço Keycloak no Docker Compose, o emissor é:

```text
http://localhost:8000/realms/product-activation
```

Configuração correspondente no `application.yml`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI:http://localhost:8000/realms/product-activation}
```

O realm `product-activation` é importado a partir de `keycloak/product-activation-realm.json`, caso ainda não exista no armazenamento do Keycloak.

Credenciais de desenvolvimento local:

| Configuração | Valor |
|---|---|
| Realm | `product-activation` |
| Client ID | `activation-api` |
| Username | `dev-test` |
| Password | `dev-test` |
| Keycloak Admin Console | `http://localhost:8000/admin` |

Essas credenciais e o fluxo de senha usados pelos scripts são exclusivamente para desenvolvimento local. Para aplicações interativas, prefira Authorization Code com PKCE.

## Endpoints

### Criar solicitação

`POST /api/v1/requests`

```json
{
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500"
}
```

A primeira criação retorna `201 Created`, com status inicial `PENDING`. A API não chama o provedor nesse request. Se o mesmo `externalId` e payload forem enviados novamente, retorna a solicitação existente; se o payload divergir, retorna `409 Conflict`.

### Consultar solicitação

`GET /api/v1/requests/{id}`

### Listar solicitações

`GET /api/v1/requests?status=PENDING&customerId=CUST-10001&page=0&size=20`

Filtros opcionais; página a partir de zero e tamanho entre 1 e 100.

## Testes da API com Shell Scripts

A pasta `product-activation-curl-tests/` contém scripts Shell para obter um token no Keycloak e executar chamadas autenticadas à API usando `curl`.

### Estrutura

```text
product-activation-curl-tests/
├── 01-get-token.sh
├── 02-test-api.sh
├── run-tests.sh
├── requests/
│   ├── request-success.json
│   ├── request-temporary-failure.json
│   ├── request-permanent-failure.json
│   └── request-idempotency-conflict.json
```

### Requisitos

- Bash
- `curl`
- `jq`
- Keycloak em execução
- Product Activation API em execução

### Configurações padrão dos scripts

| Variável | Padrão |
|---|---|
| `KEYCLOAK_URL` | `http://localhost:8000` |
| `REALM` | `product-activation` |
| `CLIENT_ID` | `activation-api` |
| `USERNAME` | `dev-test` |
| `PASSWORD` | `dev-test` |
| `API_BASE_URL` | `http://localhost:8081` |
| `REQUESTS_PATH` | `/api/v1/requests` |
| `TOKEN_FILE` | `.access_token` |

**Atenção:** a API principal deste README está documentada na porta `8080`, mas o script `02-test-api.sh` usa `8081` como padrão. Ao executar os testes com a configuração padrão do projeto, informe `API_BASE_URL=http://localhost:8080`.

### Cenários disponíveis

Os payloads ficam prontos na pasta `requests/`:

| Arquivo | Cenário | Resultado esperado |
|---|---|---|
| `request-success.json` | Ativação com sucesso | Solicitação inicialmente `PENDING` e, após processamento, conclusão com sucesso |
| `request-temporary-failure.json` | Falha temporária (`productCode` terminado em `-TEMP`) | Retentativas entre ciclos; ao atingir o limite, solicitação `FAILED` e evento `DEAD` |
| `request-permanent-failure.json` | Falha permanente (`productCode` terminado em `-FAIL`) | Solicitação `FAILED`, sem retentativas para a falha permanente |
| `request-idempotency-conflict.json` | Mesmo `externalId` do cenário de sucesso, mas com payload diferente | `409 Conflict`, se executado depois de `request-success.json` |

Cada solicitação usa um `externalId` próprio, exceto o cenário de conflito, que reutiliza intencionalmente o `externalId` do cenário de sucesso.

### Obter um token

A partir da pasta `product-activation-curl-tests/`, execute:

```bash
chmod +x *.sh
./01-get-token.sh
```

O script solicita um token ao endpoint:

```text
POST /realms/product-activation/protocol/openid-connect/token
```

O token é armazenado no arquivo `.access_token`, criado com permissões restritas. Não versione esse arquivo.

### Executar um cenário

Para obter o token e enviar uma solicitação em uma única execução, informe o payload desejado em `REQUEST_BODY_FILE`.

Exemplo — sucesso:

```bash
API_BASE_URL=http://localhost:8080 \
REQUEST_BODY_FILE=./requests/request-success.json \
./run-tests.sh
```

Falha temporária:

```bash
API_BASE_URL=http://localhost:8080 \
REQUEST_BODY_FILE=./requests/request-temporary-failure.json \
./run-tests.sh
```

Falha permanente:

```bash
API_BASE_URL=http://localhost:8080 \
REQUEST_BODY_FILE=./requests/request-permanent-failure.json \
./run-tests.sh
```

Conflito de idempotência:

```bash
API_BASE_URL=http://localhost:8080 \
REQUEST_BODY_FILE=./requests/request-idempotency-conflict.json \
./run-tests.sh
```

Para o cenário de conflito, execute primeiro o cenário de sucesso. O primeiro request cria `EXT-SUCCESS-001`; o cenário de conflito reutiliza esse identificador com outro `productCode`.

O `POST` retorna o estado inicial da solicitação. Para observar o resultado do processamento assíncrono, consulte a solicitação pelo endpoint `GET /api/v1/requests/{id}` usando o identificador retornado pela criação.

### Executar somente a chamada à API

Depois de gerar o token com `./01-get-token.sh`, também é possível executar apenas o POST:

```bash
API_BASE_URL=http://localhost:8080 \
REQUEST_BODY_FILE=./requests/request-success.json \
./02-test-api.sh
```

### Personalizar configurações

As variáveis de ambiente permitem alterar URLs e credenciais sem editar os scripts:

```bash
KEYCLOAK_URL=http://localhost:8000 \
API_BASE_URL=http://localhost:8080 \
USERNAME=dev-test \
PASSWORD=dev-test \
REQUEST_BODY_FILE=./requests/request-success.json \
./run-tests.sh
```

### Observações de segurança e execução

- O fluxo `grant_type=password` (Direct Access Grants) é usado apenas para testes locais. Para aplicações interativas, prefira Authorization Code com PKCE.
- O arquivo `.access_token` contém um token de acesso e não deve ser versionado.
- Os scripts usam `localhost` porque são executados na máquina host. Se a API ou o Keycloak forem acessados de dentro de outro container, ajuste os endereços para os nomes dos serviços na rede Docker.
- O valor do `issuer-uri` configurado na API precisa corresponder exatamente ao claim `iss` do token emitido pelo Keycloak.
- Os payloads de exemplo correspondem ao contrato documentado para `POST /api/v1/requests`; ajuste-os se o DTO da aplicação mudar.

## Transactional Outbox

A migration `V2__create_transactional_outbox.sql` cria `outbox_events`. `ActivationRequestWriter.save()` grava a solicitação e o evento `PRODUCT_ACTIVATION_REQUESTED` dentro da mesma transação de banco. Não existe chamada HTTP nessa transação.

Um worker (`OutboxProcessor`) consulta eventos elegíveis periodicamente. `OutboxClaimService` reivindica cada evento numa transação curta, com lock pessimista e lease. A transação termina antes da chamada ao provedor. Se a instância cair durante o processamento, um evento `PROCESSING` poderá ser reivindicado novamente quando `locked_until` expirar.

Depois da chamada, uma nova transação atualiza o estado da solicitação e o evento do outbox. Em falhas temporárias, o evento volta para `PENDING` com `next_attempt_at` persistido, permitindo retentativas mesmo após reinicialização. O backoff entre ciclos usa crescimento exponencial com jitter, limitado a 5 minutos; após 10 ciclos de entrega, o evento vai para `DEAD` e a solicitação para `FAILED` para intervenção operacional.

### Garantias e limites

- A solicitação e a intenção de enviá-la são confirmadas atomicamente.
- O worker entrega pelo menos uma vez; não há garantia de execução exatamente uma vez no provedor.
- O cliente HTTP envia `Idempotency-Key: externalId`. O provedor externo precisa honrar essa chave para impedir efeitos duplicados quando uma chamada foi efetivada mas a confirmação local se perdeu.
- A lease recupera eventos abandonados por falhas de processo. O valor `OUTBOX_LEASE_DURATION` deve ser maior que o timeout HTTP e a duração máxima esperada de processamento.
- Para produção, recomenda-se métrica/alerta para idade do evento mais antigo, quantidade de `DEAD`, taxa de falhas e estado do Circuit Breaker.

## Retry e Circuit Breaker por anotações

A chamada usa dois beans separados para garantir a interceptação Spring AOP:

- `ResilientProductProvider.activate()` tem `@CircuitBreaker` e envolve a sequência completa de tentativas.
- `RetriableProductProviderCall.activate()` tem `@Retry` e repete somente `TemporaryProviderFailureException`.

A separação em beans é intencional: evita o problema de self-invocation do Spring e deixa o Circuit Breaker por fora do Retry. O circuito contabiliza o resultado final da sequência, e não cada tentativa HTTP.

Parâmetros principais em `application.yml`:

- Retry: até 3 tentativas por ciclo, espera inicial de 500 ms, backoff exponencial multiplicador 2 e intervalo máximo de 5 s.
- Circuit Breaker: janela de 10 chamadas, mínimo de 5, abre com 50% de falhas, espera 10 s e permite 2 chamadas de teste em `HALF_OPEN`.
- Outbox: polling a cada 1 s, lease de 60 s, limite de 10 ciclos de entrega e adiamento de 11 s quando o Circuit Breaker está aberto. Esse adiamento não consome um ciclo de entrega.

Os valores são iniciais e devem ser ajustados com métricas e SLOs. As retentativas internas do Resilience4j são síncronas no worker; o intervalo persistente entre ciclos é controlado pelo outbox. `429`, `5xx`, timeout e falha de conexão são tratados como transitórios pelo cliente; erros permanentes não são repetidos.

## Idempotência e concorrência

`UNIQUE(external_id)` impede duplicidade da solicitação. `@Version` protege atualizações concorrentes. O claim usa lock pessimista e lease para reduzir o risco de dois workers processarem o mesmo evento ao mesmo tempo. Mesmo assim, a idempotência do provedor é necessária para cobrir a janela entre o efeito remoto e o commit local.

## Mock local

O perfil `mock-provider` é habilitado pelo Docker Compose:

- `productCode` terminado em `-TEMP`: falha transitória em todas as tentativas.
- `productCode` terminado em `-FAIL`: falha permanente.
- Outros códigos: sucesso.

Para testar os ciclos persistentes, consulte o status da solicitação após o worker processar o evento. O mock determinístico de `-TEMP` acabará em `FAILED` após o limite de ciclos, com o evento no estado `DEAD`.

## Configuração do provedor

- `PROVIDER_BASE_URL`: URL do provedor externo.
- `PROVIDER_CONNECT_TIMEOUT`: timeout de conexão (padrão 2 s).
- `PROVIDER_READ_TIMEOUT`: timeout de leitura (padrão 3 s).
- `OUTBOX_POLL_INTERVAL`: intervalo do polling em ms (padrão 1000).
- `OUTBOX_LEASE_DURATION`: duração do lease (padrão 60 s).
- `OUTBOX_MAX_DELIVERY_ATTEMPTS`: ciclos persistentes máximos (padrão 10).
- `OUTBOX_CIRCUIT_OPEN_RETRY_DELAY`: quanto esperar antes de consultar o provedor novamente com o circuito aberto (padrão 11 s).

## Limitações conhecidas

- O projeto demonstra um worker agendado no mesmo processo. Em escala maior, pode ser apropriado separar workers, usar broker e coordenar capacidade operacional.
- Não existe endpoint para consultar eventos de outbox; monitoramento deve ser feito por métricas/consultas operacionais com acesso restrito.
- Há testes unitários iniciais para idempotência da criação, gravação da solicitação/outbox, ciclo de lease/retry do evento e mapeamento de falhas transitórias. O build completo ainda precisa ser executado com `mvn verify` em ambiente com Maven e acesso às dependências.

## Uso de IA

Todo o projeto foi desenvolvido com o auxílio do ChatGPT, utilizado como ferramenta de apoio durante as etapas de desenvolvimento.


## Investigação de lentidão em produção

**1. O que investigaria primeiro?**

Começaria comparando as métricas atuais com o comportamento anterior, identificando quais endpoints foram mais afetados e em que momento a degradação começou. Também verificaria se houve deploy, alteração de configuração ou aumento no volume de requisições.

**2. Como identificaria se o problema está na aplicação, no banco ou na integração externa?**

Utilizaria métricas de latência distribuídas por etapa da requisição, além de tracing distribuído para identificar onde o tempo está sendo consumido.

- **Aplicação:** analisaria threads, pools de conexão, bloqueios e possíveis gargalos de processamento.
- **Banco:** verificaria consultas lentas, planos de execução, índices, locks e saturação do pool de conexões.
- **Integração externa:** analisaria o tempo de resposta, timeouts, erros e disponibilidade do serviço.

**3. Quais logs ou métricas seriam úteis?**

- Latência da API (p50, p95 e p99).
- Taxa de requisições e erros (RPS e error rate).
- Uso de CPU, memória e threads.
- Tempo de execução das consultas SQL.
- Métricas do pool de conexões do banco.
- Tempo de resposta e taxa de falhas das integrações externas.
- Logs estruturados com correlation ID e traces distribuídos.

Utilizaria ferramentas como Prometheus, Grafana, OpenTelemetry e logs centralizados.

**4. Que tipo de melhoria poderia ser considerada?**

Dependendo da causa identificada, consideraria otimização de consultas e índices, ajustes nos pools de conexão e threads, cache para dados frequentemente acessados e execução assíncrona de operações que não precisam bloquear a resposta da API.

Para integrações externas, avaliaria timeouts adequados, circuit breaker, retry com backoff e limites de concorrência.

**5. Os três sintomas podem ter uma causa comum?**

Sim. Uma hipótese seria a degradação do serviço externo, aumentando o tempo durante o qual as threads da aplicação ficam bloqueadas aguardando respostas. Isso pode gerar acúmulo de requisições, saturação do pool de threads ou de conexões com o banco e aumento da latência geral.

Confirmaria essa hipótese utilizando tracing distribuído, thread dumps e métricas dos pools, verificando se o aumento da latência coincide com as chamadas externas mais lentas.

**6. Como validaria que a correção funcionou depois de publicada?**

Acompanharia as métricas antes e depois do deploy, comparando principalmente p95 e p99 de latência, taxa de erros, tempo das consultas e respostas das integrações externas.

Também faria testes de carga e acompanharia o comportamento da aplicação durante um período de observação, garantindo que a melhoria não tenha introduzido novos gargalos.