# IncidentInvestigator — Analysis Integration Guideline

Bu doküman, Java/Spring Boot `IncidentInvestigator` servisi ile Python/FastAPI `IncidentAnalyzer` servisi arasındaki ilk çalışan senkron entegrasyon akışını dokümante eder.

Amaç yalnızca endpoint listesini vermek değil; analysis lifecycle, servis sorumlulukları, request sırası, beklenen sonuçlar ve hata durumlarını açık biçimde tanımlamaktır.

---

## 1. Genel Akış

```text
Client
  |
  | POST /api/v1/incidents/{id}/analyze
  v
Spring Boot - AnalysisController
  |
  v
AnalysisService
  |
  +------> PostgreSQL
  |          |
  |          v
  |    Incident + Evidence
  |
  v
IncidentAnalyzerClient
  |
  v
HttpIncidentAnalyzerClient
  |
  | HTTP POST /api/v1/analyze
  v
Python FastAPI
  |
  v
Rule-Based RootCauseAnalyzer
  |
  | RootCauseCandidate[]
  v
Spring Boot
  |
  | best candidate
  v
Incident.identifyRootCause(...)
  |
  v
PostgreSQL
```

Bu aşamada analiz senkron HTTP request-response modeli ile çalışır.

---

## 2. Servis Sorumlulukları

### Spring Boot

Spring Boot aşağıdakilerin sahibidir:

```text
Incident lifecycle
Evidence persistence
Analysis orchestration
Python servis çağrısı
Candidate seçimi
Minimum confidence kontrolü
RootCause persistence
HTTP error mapping
```

Spring şu kararları verir:

```text
Incident analiz edilebilir mi?
Evidence var mı?
Hangi candidate seçilecek?
Candidate yeterince güvenilir mi?
RootCause Incident'a bağlanmalı mı?
```

### Python

Python servisinin sorumluluğu yalnızca analizdir:

```text
Incident + Evidence al
↓
Rule'ları çalıştır
↓
Root cause candidate üret
↓
Confidence hesapla
↓
Supporting evidence döndür
```

Python:

```text
Incident status değiştirmez.
PostgreSQL'e yazmaz.
Incident resolve etmez.
Spring'e callback yapmaz.
```

Python bir `analysis capability` olarak davranır.

---

## 3. Analysis Ön Koşulları

`POST /api/v1/incidents/{id}/analyze` çağrısından önce Incident:

```text
IN_INVESTIGATION
```

durumunda olmalıdır.

Ayrıca en az bir Evidence bulunmalıdır.

Doğru lifecycle:

```text
CREATE
  ↓
OPEN
  ↓
START INVESTIGATION
  ↓
IN_INVESTIGATION
  ↓
ADD EVIDENCE
  ↓
ANALYZE
  ↓
ROOT CAUSE IDENTIFIED
```

Analysis işlemi Incident'ı otomatik olarak `RESOLVED` durumuna geçirmez.

---

## 4. E2E Test Sırası

### Step 1 — Incident oluştur

```http
POST /api/v1/incidents
```

Örnek body:

```json
{
  "title": "Payment service latency",
  "description": "Payment requests are timing out",
  "incidentType": "LATENCY",
  "source": "payment-service",
  "occurredAt": "2026-08-24T16:30:00"
}
```

Response'tan oluşturulan `id` değerini al.

### Step 2 — Investigation başlat

```http
POST /api/v1/incidents/{id}/investigation
```

Beklenen state:

```text
OPEN
↓
IN_INVESTIGATION
```

### Step 3 — LOG Evidence ekle

```http
POST /api/v1/incidents/{id}/evidence
```

```json
{
  "type": "LOG",
  "source": "payment-service",
  "content": "HikariPool - Connection is not available",
  "observedAt": "2026-08-24T16:31:00"
}
```

### Step 4 — METRIC Evidence ekle

```http
POST /api/v1/incidents/{id}/evidence
```

```json
{
  "type": "METRIC",
  "source": "payment-service",
  "content": "db_connection_pool_usage=100",
  "observedAt": "2026-08-24T16:32:00"
}
```

Bu iki evidence birlikte Python rule engine içindeki:

```text
DATABASE_CONNECTION_POOL_EXHAUSTION
```

rule'unu tetikler.

### Step 5 — Analysis çağır

```http
POST /api/v1/incidents/{id}/analyze
```

Body gerektirmez.

Spring Boot:

```text
Incident ID
↓
IncidentRepository
↓
Incident + Evidence
↓
AnalysisRequest
↓
Python
```

akışını kendi içinde gerçekleştirir.

---

## 5. Python'a Gönderilen Request

```json
{
  "incidentId": 1,
  "title": "Payment service latency",
  "incidentType": "LATENCY",
  "evidence": [
    {
      "type": "LOG",
      "source": "payment-service",
      "content": "HikariPool - Connection is not available",
      "observedAt": "2026-08-24T16:31:00"
    },
    {
      "type": "METRIC",
      "source": "payment-service",
      "content": "db_connection_pool_usage=100",
      "observedAt": "2026-08-24T16:32:00"
    }
  ]
}
```

HTTP contract hem Java hem Python tarafında `camelCase` kullanır.

---

## 6. Python Analyzer Response

```json
{
  "incidentId": 1,
  "candidates": [
    {
      "rootCause": "DATABASE_CONNECTION_POOL_EXHAUSTION",
      "confidence": 0.91,
      "explanation": "Database connection pool saturation matches the observed timeout symptoms.",
      "supportingEvidence": [
        "HikariPool - Connection is not available",
        "db_connection_pool_usage=100"
      ]
    }
  ]
}
```

Python bir veya daha fazla candidate dönebilir.

Java candidate sırasına güvenmez; en yüksek `confidence` değerine sahip candidate Java tarafından seçilir.

---

## 7. Başarılı `/analyze` Response

Gerçek E2E testinde başarıyla alınan response:

```json
{
  "incidentId": 1,
  "status": "ROOT_CAUSE_IDENTIFIED",
  "rootCause": "DATABASE_CONNECTION_POOL_EXHAUSTION",
  "confidence": 0.91,
  "explanation": "Database connection pool saturation matches the observed timeout symptoms.",
  "supportingEvidence": [
    "HikariPool - Connection is not available",
    "db_connection_pool_usage=100"
  ]
}
```

Beklenen HTTP status:

```text
200 OK
```

Bu sonuç ilk synchronous Spring Boot ↔ Python analysis flow'unun başarılı biçimde çalıştığını gösterir.

---

## 8. Root Cause Persistence Kontrolü

Analysis sonrasında:

```http
GET /api/v1/incidents/{id}
```

çağrılmalıdır.

Beklenen:

```text
status = IN_INVESTIGATION
```

kalmalıdır.

RootCause ise persist edilmiş olmalıdır:

```json
{
  "status": "IN_INVESTIGATION",
  "rootCause": {
    "rootCauseType": "DATABASE_CONNECTION_POOL_EXHAUSTION",
    "confirmed": false
  }
}
```

`confirmed=false` olmalıdır çünkü root cause otomatik analyzer tarafından üretilmiştir; henüz operator/human confirmation yapılmamıştır.

---

## 9. Analysis Sonrası Neden Incident Resolve Edilmiyor?

Analysis ile resolve farklı işlemlerdir:

```text
Analyze
→ probable root cause identification

Resolve
→ incident lifecycle decision
```

Bu nedenle analyze sonrasında `IN_INVESTIGATION` state'i korunur.

Resolve ayrı endpoint ile yapılır:

```http
POST /api/v1/incidents/{id}/resolve
```

Bu separation automated reasoning ile domain lifecycle kontrolünü birbirinden ayırır.

---

## 10. Confidence Handling

Java tarafında minimum confidence threshold bulunur.

Örnek:

```text
MIN_CONFIDENCE = 0.60
```

Davranış:

```text
confidence >= 0.60
    ↓
ROOT_CAUSE_IDENTIFIED
    ↓
RootCause Incident'a bağlanır
```

```text
confidence < 0.60
    ↓
INCONCLUSIVE
    ↓
RootCause persist edilmez
```

Düşük güvenli analyzer çıktıları otomatik olarak domain state'e yazılmaz.

---

## 11. UNKNOWN Handling

Python evidence yetersiz olduğunda:

```json
{
  "rootCause": "UNKNOWN",
  "confidence": 0.20
}
```

gibi bir candidate üretebilir.

Java bu durumda `UNKNOWN` değerini RootCause entity olarak persist etmez.

Beklenen:

```text
Analysis result = INCONCLUSIVE
Incident.status = IN_INVESTIGATION
Incident.rootCause = null
```

Bu davranış, “bilmiyoruz” sonucunun gerçek bir root cause gibi saklanmasını engeller.

---

## 12. Supporting Evidence

Python yalnızca root cause adı döndürmez; hangi evidence'ların bu sonuca katkı verdiğini de döndürür:

```json
"supportingEvidence": [
  "HikariPool - Connection is not available",
  "db_connection_pool_usage=100"
]
```

Bu alan:

```text
explainability
debugging
operator review
future LLM reasoning
```

için kullanılabilir.

İlk versiyonda supporting evidence ayrıca DB'de persist edilmez; API response'ta caller'a döndürülür.

---

## 13. Error Semantics

### Incident analiz edilemiyorsa

Örnek:

```text
Incident = OPEN
```

veya:

```text
Evidence = empty
```

Beklenen:

```text
409 CONFLICT
```

### Python servisi unavailable ise

Örnek:

```text
connection refused
timeout
Python process down
```

Beklenen:

```text
503 SERVICE_UNAVAILABLE
```

Incident değişmemelidir:

```text
status unchanged
rootCause unchanged
```

### Python invalid response dönerse

Örnek:

```text
incidentId mismatch
empty candidates
invalid candidate
```

Beklenen:

```text
502 BAD_GATEWAY
```

---

## 14. Python Servisi Kapalıyken Test

Python servisini durdur.

Sonra:

```http
POST /api/v1/incidents/{id}/analyze
```

çağır.

Beklenen:

```text
HTTP 503
```

Sonra:

```http
GET /api/v1/incidents/{id}
```

ile doğrula:

```text
status unchanged
rootCause unchanged
```

Downstream servis failure domain state'i bozmamalıdır.

---

## 15. Current Synchronous Architecture

Şu anki analysis architecture:

```text
Spring Boot
    |
    | synchronous HTTP
    v
Python FastAPI
```

Avantajları:

```text
basit debugging
kolay contract validation
Swagger/curl ile test
deterministic request-response
ilk entegrasyon için düşük complexity
```

Trade-off:

```text
remote Python call boyunca request açık kalır
Python unavailable ise analysis anında fail olur
uzun analysis workload'ları için uygun değildir
```

Bu synchronous flow bilinçli olarak ilk milestone için seçilmiştir.

---

## 16. Transport Boundary

Spring application layer Python/FastAPI detaylarını bilmez.

```text
AnalysisService
      |
      v
IncidentAnalyzerClient
      |
      v
HttpIncidentAnalyzerClient
      |
      v
Python
```

Bu abstraction ileride şu adapter'lara izin verir:

```text
HttpIncidentAnalyzerClient
GrpcIncidentAnalyzerClient
AsyncIncidentAnalyzerClient
```

Application layer değiştirilmeden transport değiştirilebilir.

---

## 17. Domain ve Transport Contract Ayrımı

Python response doğrudan `RootCause` entity olarak deserialize edilmez.

Doğru flow:

```text
Python JSON
↓
AnalysisResponse DTO
↓
RootCauseCandidateResponse
↓
AnalysisService
↓
RootCause domain object
```

Bu translation boundary domain modelini external service contract'ından korur.

---

## 18. Current Milestone Status

Aşağıdaki flow gerçek ortamda başarıyla doğrulanmıştır:

```text
Incident create                  ✅
Investigation start              ✅
LOG evidence                     ✅
METRIC evidence                  ✅
Spring → Python HTTP             ✅
Python rule-based RCA            ✅
Python → Spring response         ✅
Best candidate selection         ✅
RootCause persistence            ✅
confirmed=false                  ✅
Incident remains investigation   ✅
```

Bu milestone projenin ilk gerçek:

```text
cross-runtime microservice integration
```

akışıdır.

---

## 19. Sonraki Yol Haritası

### Milestone 2 — Failure ve contract hardening

```text
HTTP client tests
timeout scenarios
invalid downstream response
inconclusive scenarios
duplicate analysis behavior
```

### Milestone 3 — Kafka Async Analysis

```text
Spring
  |
  | analysis job event
  v
Kafka
  |
  v
Python Worker
  |
  | result event
  v
Kafka
  |
  v
Spring
```

Bu milestone ile:

```text
retry
DLQ
idempotency
eventual consistency
job state
duplicate delivery
```

gibi distributed systems problemleri çalışılabilir.

### Milestone 4 — gRPC Adapter

Synchronous transport için REST'e alternatif:

```text
Spring
  |
  | protobuf / HTTP2
  v
Python
```

Yeni adapter:

```text
GrpcIncidentAnalyzerClient
```

olarak eklenebilir.

Mevcut `IncidentAnalyzerClient` interface'i korunur.

### Milestone 5 — Advanced Analyzer

Python tarafında:

```text
Rule engine
↓
Structured metrics
↓
Cross-evidence correlation
↓
Statistical anomaly detection
↓
LLM reasoning
```

şeklinde ilerlenebilir.

---

## 20. Guideline Özeti

1. Spring domain lifecycle'ın sahibidir.
2. Python yalnızca analysis capability'dir.
3. Analysis yalnızca `IN_INVESTIGATION` Incident üzerinde çalışır.
4. Evidence olmadan analyzer çağrılmaz.
5. External analyzer DTO'ları domain entity değildir.
6. Java best candidate'ı kendisi seçer.
7. `UNKNOWN` root cause olarak persist edilmez.
8. Düşük confidence sonuçlar inconclusive kabul edilir.
9. Automated root cause `confirmed=false` olarak tutulur.
10. Analyze işlemi Incident'ı otomatik resolve etmez.
11. Python failure durumunda Incident state değişmemelidir.
12. HTTP implementation `IncidentAnalyzerClient` arkasında izole edilmiştir.
13. Supporting evidence analysis explainability için korunur.
14. İlk milestone synchronous REST'tir.
15. Async Kafka ve gRPC daha sonraki bağımsız transport/workflow milestone'larıdır.

---

## 21. Başarıyla Doğrulanan Örnek

Input evidence:

```text
LOG:
HikariPool - Connection is not available

METRIC:
db_connection_pool_usage=100
```

Gerçek analysis sonucu:

```text
HTTP 200

status:
ROOT_CAUSE_IDENTIFIED

rootCause:
DATABASE_CONNECTION_POOL_EXHAUSTION

confidence:
0.91
```

Supporting evidence:

```text
HikariPool - Connection is not available
db_connection_pool_usage=100
```

Bu sonuç Java/Spring Boot orchestration ile Python/FastAPI rule-based analyzer arasındaki ilk uçtan uca integration milestone'unun başarıyla tamamlandığını doğrular.
