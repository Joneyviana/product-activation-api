# Product Activation API — versão 4

API REST para registrar e consultar solicitações de ativação de produtos, com Transactional Outbox, processamento assíncrono e resiliência via anotações do Resilience4j.

## Stack

- Java 21, Spring Boot 3.5, Spring MVC e Spring Data JPA
- PostgreSQL 16 e Flyway
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

O projeto está configurado como OAuth2 Resource Server. Configure `JWT_ISSUER_URI` para um emissor JWT real antes de chamar os endpoints autenticados; o Compose não inicia um servidor de identidade.

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

Antes da entrega, revise o código, execute os testes e registre as ferramentas e usos de IA efetivamente utilizados.
