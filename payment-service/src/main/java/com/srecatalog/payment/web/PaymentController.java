package com.srecatalog.payment.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.common.model.Subject;
import com.srecatalog.payment.config.PaymentProperties;
import com.srecatalog.payment.service.PaymentService;
import com.srecatalog.payment.service.SubjectAccess;
import com.srecatalog.payment.web.dto.CancelPaymentRequest;
import com.srecatalog.payment.web.dto.CreatePaymentRequest;
import com.srecatalog.payment.web.dto.PaymentEligibleApproversResponse;
import com.srecatalog.payment.web.dto.PaymentResponse;
import com.srecatalog.payment.web.dto.RejectPaymentRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RequestSubjectResolver subjectResolver;
    private final PaymentProperties properties;

    public PaymentController(
            PaymentService paymentService,
            RequestSubjectResolver subjectResolver,
            PaymentProperties properties) {
        this.paymentService = paymentService;
        this.subjectResolver = subjectResolver;
        this.properties = properties;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request, HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        var record = paymentService.create(
                request.instructionId(),
                request.valueDate(),
                request.amount(),
                subject,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest));
        return PaymentResponseMapper.toResponse(record);
    }

    @GetMapping
    public List<PaymentResponse> list(
            @RequestParam(required = false) String instructionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return paymentService.list(subject, instructionId, status, Math.min(limit, 500)).stream()
                .map(PaymentResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable String paymentId, HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return PaymentResponseMapper.toResponse(paymentService.get(paymentId, subject));
    }

    @PutMapping("/{paymentId}")
    public PaymentResponse update(
            @PathVariable String paymentId,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return PaymentResponseMapper.toResponse(paymentService.update(
                paymentId,
                request.instructionId(),
                request.valueDate(),
                request.amount(),
                subject,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{paymentId}/submit")
    public PaymentResponse submit(@PathVariable String paymentId, HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return PaymentResponseMapper.toResponse(paymentService.submit(
                paymentId,
                subject,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{paymentId}/approve")
    public PaymentResponse approve(@PathVariable String paymentId, HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return PaymentResponseMapper.toResponse(paymentService.approve(
                paymentId,
                subject,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{paymentId}/reject")
    public PaymentResponse reject(
            @PathVariable String paymentId,
            @Valid @RequestBody RejectPaymentRequest request,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return PaymentResponseMapper.toResponse(paymentService.reject(
                paymentId,
                subject,
                request,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{paymentId}/cancel")
    public PaymentResponse cancel(
            @PathVariable String paymentId,
            @RequestBody(required = false) CancelPaymentRequest request,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        return PaymentResponseMapper.toResponse(paymentService.cancel(
                paymentId,
                subject,
                request,
                subjectResolver.bearerToken(httpRequest),
                subjectResolver.sessionId(httpRequest)));
    }

    @PostMapping("/{paymentId}/eligible-approvers")
    public PaymentEligibleApproversResponse eligibleApprovers(
            @PathVariable String paymentId,
            HttpServletRequest httpRequest) {
        Subject subject = subjectResolver.resolveActor(httpRequest);
        SubjectAccess.requireCompliance(subject, properties);
        Map<String, Object> data = paymentService.eligibleApprovers(paymentId);
        return new PaymentEligibleApproversResponse(
                stringValue(data, "payment_id"),
                stringValue(data, "instruction_id"),
                stringValue(data, "payment_status"),
                doubleValue(data, "amount"),
                stringValue(data, "currency"),
                stringValue(data, "owning_lob"),
                stringValue(data, "instruction_status"),
                stringValue(data, "evaluated_at"),
                listValue(data, "eligible"),
                listValue(data, "prospective_eligible"),
                intValue(data, "candidates_evaluated"),
                stringValue(data, "approval_blocked_reason"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private static String stringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static double doubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static int intValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
