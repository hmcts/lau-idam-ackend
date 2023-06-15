package uk.gov.hmcts.reform.laubackend.idam.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.laubackend.idam.domain.UserDeletionAudit;
import uk.gov.hmcts.reform.laubackend.idam.dto.DeletionLog;
import uk.gov.hmcts.reform.laubackend.idam.dto.DeletionLogGetRequestParams;
import uk.gov.hmcts.reform.laubackend.idam.repository.UserDeletionAuditFindRepository;
import uk.gov.hmcts.reform.laubackend.idam.repository.UserDeletionAuditInsertRepository;
import uk.gov.hmcts.reform.laubackend.idam.repository.UserDeletionAuditRepository;
import uk.gov.hmcts.reform.laubackend.idam.response.UserDeletionGetResponse;
import uk.gov.hmcts.reform.laubackend.idam.utils.TimestampUtil;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.springframework.data.domain.PageRequest.of;

@Service
@RequiredArgsConstructor
public class UserDeletionAuditService {

    private final TimestampUtil timestampUtil;
    private final UserDeletionAuditInsertRepository userDeletionAuditInsertRepository;
    private final UserDeletionAuditRepository userDeletionAuditRepository;
    private final UserDeletionAuditFindRepository userDeletionAuditFindRepository;

    @Value("${security.db.backend-encryption-key}")
    private String securityDbBackendEncryptionKey;

    @Value("${security.db.encryption-enabled}")
    private Boolean encryptionEnabled;

    @Value("${default.page.size}")
    private String defaultPageSize;

    public UserDeletionGetResponse getUserDeletions(final DeletionLogGetRequestParams params) {
        final Page<UserDeletionAudit> deletionAudits = userDeletionAuditFindRepository.findUserDeletion(
            params,
            securityDbBackendEncryptionKey,
            getPage(params.size(), params.page())
        );

        final List<DeletionLog> deletionLogList = deletionAudits.getContent().stream()
            .map(DeletionLog::toDto)
            .toList();

        return UserDeletionGetResponse.builder()
            .deletionLogs(deletionLogList)
            .moreRecords(deletionAudits.hasNext())
            .startRecordNumber(calculateStartRecordNumber(deletionAudits))
            .build();
    }

    public List<DeletionLog> saveUserDeletion(final List<DeletionLog> deletionLogs) {
        final List<DeletionLog> responses = new ArrayList<>(deletionLogs.size());

        for (DeletionLog log: deletionLogs) {
            final UserDeletionAudit userDeletionAudit = UserDeletionAudit.builder()
                .userId(log.getUserId())
                .emailAddress(log.getEmailAddress())
                .firstName(log.getFirstName())
                .lastName(log.getLastName())
                .timestamp(timestampUtil.getUtcTimestampValue(log.getDeletionTimestamp()))
                .build();

            final UserDeletionAudit userDeletionAuditResponse;

            if (BooleanUtils.isTrue(encryptionEnabled)) {
                userDeletionAuditResponse = userDeletionAuditInsertRepository
                    .saveUserDeleteAuditWithEncryption(userDeletionAudit, securityDbBackendEncryptionKey);
            } else {
                userDeletionAuditResponse = userDeletionAuditRepository.save(userDeletionAudit);
            }
            responses.add(DeletionLog.toDto(userDeletionAuditResponse));
        }
        return responses;
    }

    private Pageable getPage(final String size, final String page) {
        final String pageSize = isEmpty(size) ? defaultPageSize : size.trim();
        final String pageNumber = isEmpty(page) ? "1" : page.trim();

        return of(parseInt(pageNumber) - 1, parseInt(pageSize), Sort.by(Sort.Direction.DESC, "deletion_timestamp"));
    }

    private long calculateStartRecordNumber(final Page<UserDeletionAudit> users) {
        return users.getSize() * users.getNumber() + 1L;
    }


}