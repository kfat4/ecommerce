package com.ecommerce.payment.application;

import com.ecommerce.common.contracts.PaymentCompletedEvent;
import com.ecommerce.common.contracts.PaymentFailedEvent;
import com.ecommerce.common.contracts.ProcessPaymentCommand;
import com.ecommerce.common.contracts.Topics;
import com.ecommerce.common.messaging.IncomingMessage;
import com.ecommerce.common.messaging.MessageReader;
import com.ecommerce.common.messaging.NonRetryableMessageException;
import com.ecommerce.common.outbox.OutboxPublisher;
import com.ecommerce.common.util.UuidV7;
import com.ecommerce.payment.domain.Payment;
import com.ecommerce.payment.domain.PaymentRepository;
import com.ecommerce.payment.provider.PaymentProviderClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Why no inbox table here? Because a remote side effect sits in the middle:
 * <pre>
 *   tx1: insert payment PENDING (unique order_id)   ← business-key idempotency
 *   ---  call provider (no DB transaction open!)   ← provider-side idempotency (Idempotency-Key)
 *   tx2: mark COMPLETED/FAILED + outbox reply
 * </pre>
 * If we crash between tx1 and tx2, the command is redelivered, we find the PENDING payment and simply call the
 * provider again with the same key – it returns the original result. An inbox row committed in tx1 would instead
 * make us skip the redelivery and leave the payment PENDING forever.
 * If the payment is already final, we just re-send the reply (the orchestrator ignores duplicates).
 */
@Component
public class PaymentCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandHandler.class);

    private final MessageReader reader;
    private final PaymentRepository payments;
    private final PaymentProviderClient provider;
    private final OutboxPublisher outbox;
    private final TransactionTemplate transaction;

    public PaymentCommandHandler(MessageReader reader, PaymentRepository payments, PaymentProviderClient provider,
                                 OutboxPublisher outbox, TransactionTemplate transaction) {
        this.reader = reader;
        this.payments = payments;
        this.provider = provider;
        this.outbox = outbox;
        this.transaction = transaction;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS)
    public void onCommand(ConsumerRecord<String, String> record) {
        IncomingMessage message = reader.read(record);
        if (!(message.payload() instanceof ProcessPaymentCommand command)) {
            throw new NonRetryableMessageException("Unexpected command " + message.type());
        }

        Payment payment = findOrCreate(command);
        if (payment.getStatus().isFinal()) {
            log.info("Payment for order={} already {} – re-sending reply", command.orderId(), payment.getStatus());
            transaction.executeWithoutResult(s -> publishResult(payment));
            return;
        }

        var response = provider.charge(command.orderId(), command.amount(), command.currency());

        transaction.executeWithoutResult(status -> {
            Payment current = payments.findByOrderId(command.orderId()).orElseThrow();
            if (response.approved()) {
                current.complete(response.id());
            } else {
                current.fail(response.declineReason() == null ? "DECLINED" : response.declineReason());
            }
            log.info("Payment order={} amount={} {} -> {}", command.orderId(), command.amount(), command.currency(),
                    current.getStatus());
            publishResult(current);
        });
    }

    private Payment findOrCreate(ProcessPaymentCommand command) {
        return payments.findByOrderId(command.orderId()).orElseGet(() -> {
            try {
                return transaction.execute(s -> payments.saveAndFlush(new Payment(UuidV7.next(), command.orderId(),
                        command.customerId(), command.amount(), command.currency())));
            } catch (DataIntegrityViolationException raceWithOtherReplica) {
                return payments.findByOrderId(command.orderId()).orElseThrow();
            }
        });
    }

    private void publishResult(Payment payment) {
        Object event = switch (payment.getStatus()) {
            case COMPLETED -> new PaymentCompletedEvent(payment.getOrderId(), payment.getId(), payment.getProviderReference());
            case FAILED -> new PaymentFailedEvent(payment.getOrderId(), payment.getFailureReason());
            case PENDING -> throw new IllegalStateException("Cannot publish a pending payment");
        };
        outbox.publish(Topics.PAYMENT_EVENTS, "Order", payment.getOrderId().toString(), event);
    }
}
