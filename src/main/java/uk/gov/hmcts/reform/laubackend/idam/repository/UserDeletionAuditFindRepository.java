package uk.gov.hmcts.reform.laubackend.idam.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.laubackend.idam.domain.UserDeletionAudit;
import uk.gov.hmcts.reform.laubackend.idam.dto.UserDeletionUser;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class UserDeletionAuditFindRepository {

    private static final int MAX_RESULT_COUNT = 10_000;

    private static final String SELECT = """
        SELECT
            id,
            user_id,
            decrypt_value(email_address, :encryptionKey) as email_address,
            decrypt_value(first_name, :encryptionKey) as first_name,
            decrypt_value(last_name, :encryptionKey) as last_name,
            deletion_timestamp FROM user_deletion_audit
        WHERE""";
    private static final String TIME_RANGE_CRITERIA =
        "deletion_timestamp >= :startTime AND deletion_timestamp <= :endTime";


    private static final String USER_CRITERIA = "AND user_id = :userId";
    private static final String EMAIL_CRITERIA = "AND email_address_hmac = hash_value(:emailAddress, :encryptionKey)";
    private static final String FIRST_NAME_CRITERIA = "AND first_name_hmac = hash_value(:firstName, :encryptionKey)";
    private static final String LAST_NAME_CRITERIA = "AND last_name_hmac = hash_value(:lastName, :encryptionKey)";

    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<UserDeletionAudit> findUserDeletion(
        final UserDeletionUser userDeletionUser,
        final Timestamp startTime,
        final Timestamp endTime,
        final String encryptionKey,
        final Pageable pageable) {

        final List<String> queryParts = new LinkedList<>();
        queryParts.add(SELECT);
        queryParts.add(TIME_RANGE_CRITERIA);

        Map<String, String> usedParams = addSearchCriteria(queryParts, userDeletionUser);

        final String queryString = String.join(" ", queryParts);
        final Query query = entityManager.createNativeQuery(queryString, UserDeletionAudit.class);
        setQueryParams(query, usedParams, startTime, endTime);
        query.setParameter("encryptionKey", encryptionKey);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        final List<UserDeletionAudit> results = query.getResultList();

        long totalCount = countResults(userDeletionUser, startTime, endTime,  encryptionKey);

        return new PageImpl<>(results, pageable, totalCount);
    }

    private long countResults(final UserDeletionUser userDeletionUser,
                              final Timestamp startTime,
                              final Timestamp endTime,
                              final String encryptionKey) {

        final List<String> clauses = new LinkedList<>();

        String fullQuery = "SELECT count(*) FROM (%s) AS tbl";
        String innerQuery = "SELECT 1 FROM user_deletion_audit WHERE %s LIMIT %s";

        clauses.add(TIME_RANGE_CRITERIA);
        Map<String, String> usedParams = addSearchCriteria(clauses, userDeletionUser);
        innerQuery = String.format(innerQuery, String.join(" ", clauses), MAX_RESULT_COUNT);
        fullQuery = String.format(fullQuery, innerQuery);
        final Query query = entityManager.createNativeQuery(fullQuery);
        setQueryParams(query, usedParams, startTime, endTime);

        List<String> fieldsWithEncryption = Arrays.asList("emailAddress", "firstName", "lastName");
        boolean requireEncryptionKey = fieldsWithEncryption.stream().anyMatch(usedParams::containsKey);

        if (requireEncryptionKey) {
            query.setParameter("encryptionKey", encryptionKey);
        }

        return ((Number) query.getSingleResult()).intValue();
    }

    private void setQueryParams(final Query query,
                                final Map<String, String> usedParams,
                                final Timestamp startTime,
                                final Timestamp endTime) {

        usedParams.forEach(query::setParameter);
        query.setParameter("startTime", startTime);
        query.setParameter("endTime", endTime);
    }

    private Map<String, String> addSearchCriteria(List<String> queryParts, final UserDeletionUser userDeletionUser) {
        Map<String, String> usedParams = new ConcurrentHashMap<>();
        if (userDeletionUser == null) {
            return usedParams;
        }
        if (!StringUtils.isEmpty(userDeletionUser.userId())) {
            queryParts.add(USER_CRITERIA);
            usedParams.put("userId", userDeletionUser.userId());
        }
        if (!StringUtils.isEmpty(userDeletionUser.emailAddress())) {
            queryParts.add(EMAIL_CRITERIA);
            usedParams.put("emailAddress", userDeletionUser.emailAddress());
        }
        if (!StringUtils.isEmpty(userDeletionUser.firstName())) {
            queryParts.add(FIRST_NAME_CRITERIA);
            usedParams.put("firstName", userDeletionUser.firstName());
        }
        if (!StringUtils.isEmpty(userDeletionUser.lastName())) {
            queryParts.add(LAST_NAME_CRITERIA);
            usedParams.put("lastName", userDeletionUser.lastName());
        }
        return usedParams;
    }

}
