#!/usr/bin/env bash
# Teste ponta a ponta contra o app rodando via docker compose.
# Uso: docker compose up --build -d && ./scripts/smoke-test.sh
set -euo pipefail

API=${API_URL:-http://localhost:8083}
SECRET=${WEBHOOK_SECRET:-segredo-local}

# pega a primeira ocorrencia do campo (na lista de faturas, a fatura vem antes das tentativas)
json_field() { grep -o "\"$1\":\"\{0,1\}[^\",}]*" | head -1 | sed "s/^\"$1\":\"\{0,1\}//"; }

post() { curl -sf -X POST "$API$1" -H 'Content-Type: application/json' -d "$2"; }

# assina o corpo como o gateway faria: HMAC-SHA256 em hex
send_webhook() {
  local body=$1
  local sig
  sig=$(printf '%s' "$body" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.* //')
  curl -s -X POST "$API/webhooks/payments" -H 'Content-Type: application/json' \
    -H "X-Signature: sha256=$sig" -d "$body"
}

invoice_status() { curl -sf "$API/subscriptions/$1/invoices" | json_field status; }
charge_id() { curl -sf "$API/subscriptions/$1/invoices" | json_field chargeId; }

SUFFIX=$RANDOM
PLAN=$(post /plans "{\"name\":\"Pro $SUFFIX\",\"price\":49.90,\"interval\":\"MONTHLY\"}" | json_field id)
CUSTOMER=$(post /customers "{\"name\":\"Cliente\",\"email\":\"cliente$SUFFIX@teste.com\"}" | json_field id)

echo "1) assinar e pagar: a primeira fatura e cobrada na hora e fica OPEN ate o webhook"
SUB=$(post /subscriptions "{\"customerId\":\"$CUSTOMER\",\"planId\":\"$PLAN\",\"paymentToken\":\"tok_ok\"}" | json_field id)
echo "   fatura: $(invoice_status "$SUB")"
CHARGE=$(charge_id "$SUB")
echo "   webhook sem assinatura valida:"
echo "   -> HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/webhooks/payments" -H 'Content-Type: application/json' -d '{}')"
echo "   webhook assinado (payment.succeeded):"
BODY="{\"eventId\":\"evt_$SUFFIX\",\"type\":\"payment.succeeded\",\"chargeId\":\"$CHARGE\"}"
echo "   -> $(send_webhook "$BODY")"
echo "   mesmo evento de novo:"
echo "   -> $(send_webhook "$BODY")"
[ "$(invoice_status "$SUB")" = "PAID" ] || { echo "FALHOU: fatura nao ficou PAID"; exit 1; }
echo "   fatura: PAID"

echo "2) cartao recusado: fatura FAILED com retry agendado e assinatura PAST_DUE"
SUB2=$(post /subscriptions "{\"customerId\":\"$CUSTOMER\",\"planId\":\"$PLAN\",\"paymentToken\":\"tok_recusado\"}" | json_field id)
CHARGE2=$(charge_id "$SUB2")
send_webhook "{\"eventId\":\"evt_f$SUFFIX\",\"type\":\"payment.failed\",\"chargeId\":\"$CHARGE2\",\"reason\":\"Cartao sem limite\"}" > /dev/null
[ "$(invoice_status "$SUB2")" = "FAILED" ] || { echo "FALHOU: fatura nao ficou FAILED"; exit 1; }
echo "   fatura: FAILED, assinatura: $(curl -sf "$API/subscriptions/$SUB2" | json_field status)"

echo "3) gateway fora do ar: a falha vira retry em vez de erro para o cliente"
SUB3=$(post /subscriptions "{\"customerId\":\"$CUSTOMER\",\"planId\":\"$PLAN\",\"paymentToken\":\"tok_gateway_down\"}" | json_field id)
[ "$(invoice_status "$SUB3")" = "FAILED" ] || { echo "FALHOU: esperado FAILED"; exit 1; }
echo "   fatura: FAILED (retry em 24h)"

echo "OK"
