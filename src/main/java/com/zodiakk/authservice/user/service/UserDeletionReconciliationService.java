package com.zodiakk.authservice.user.service;

import com.zodiakk.authservice.common.constants.KafkaEventConstants;
import com.zodiakk.authservice.outbox.entity.OutboxEventStatus;
import com.zodiakk.authservice.outbox.repository.OutboxEventRepository;
import com.zodiakk.authservice.user.entity.AccountDeletionStatus;
import com.zodiakk.authservice.user.entity.User;
import com.zodiakk.authservice.user.event.UserEventService;
import com.zodiakk.authservice.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletionReconciliationService {

    private static final List<OutboxEventStatus> IN_FLIGHT_STATUSES = List.of(OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING);

    private final UserRepository userRepository;

    private final OutboxEventRepository outboxEventRepository;

    private final UserEventService userEventService;

    @Value("${app.deletion.stuck-after-seconds}")
    private long stuckAfterSeconds;

    @Value("${app.deletion.reconcile-batch-size}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${app.deletion.reconcile-delay-ms}",
            initialDelayString = "${app.deletion.reconcile-initial-delay-ms}"
    )
    @Transactional
    public void republishStuckDeletions() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC)
                .minusSeconds(stuckAfterSeconds);

        List<User> stuckUsers = userRepository.findStuckDeletions(
                AccountDeletionStatus.DELETING,
                threshold,
                PageRequest.of(0, batchSize)
        );

        for (User user : stuckUsers) {
            boolean alreadyInFlight = outboxEventRepository
                    .existsByAggregateIdAndEventTypeAndStatusIn(
                            user.getEmployeeId(),
                            KafkaEventConstants.USER_DELETED,
                            IN_FLIGHT_STATUSES
                    );

            if (alreadyInFlight) {
                continue;
            }

            userEventService.recordUserDeleted(
                    user.getId(),
                    user.getEmployeeId()
            );

            log.warn(
                    "Re-published USER_DELETED for stuck deletion: userId={}, employeeId={}",
                    user.getId(),
                    user.getEmployeeId()
            );
        }
    }
}
