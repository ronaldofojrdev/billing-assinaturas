package dev.ronaldofojr.billing.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final SignatureVerifier verifier;
    private final WebhookService service;
    private final ObjectMapper objectMapper;

    public WebhookController(SignatureVerifier verifier, WebhookService service, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    // Recebe o corpo como String porque a assinatura vale para os bytes originais, nao para o JSON reserializado.
    @PostMapping("/payments")
    public ResponseEntity<Map<String, String>> receive(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String body) {
        if (!verifier.isValid(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "assinatura invalida"));
        }

        PaymentWebhook event;
        try {
            event = objectMapper.readValue(body, PaymentWebhook.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "json invalido"));
        }
        if (!event.isComplete()) {
            return ResponseEntity.badRequest().body(Map.of("error", "eventId, type e chargeId sao obrigatorios"));
        }

        WebhookResult result;
        try {
            result = service.handle(event);
        } catch (DataIntegrityViolationException e) {
            result = WebhookResult.DUPLICATE; // o mesmo evento chegou duas vezes ao mesmo tempo
        }

        // 2xx para o gateway parar de reenviar; 404 so quando queremos que ele tente de novo depois
        HttpStatus status = result == WebhookResult.UNKNOWN_CHARGE ? HttpStatus.NOT_FOUND : HttpStatus.OK;
        return ResponseEntity.status(status).body(Map.of("result", result.name()));
    }
}
