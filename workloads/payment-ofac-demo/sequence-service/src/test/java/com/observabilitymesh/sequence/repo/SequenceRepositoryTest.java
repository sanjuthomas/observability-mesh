package com.observabilitymesh.sequence.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SequenceRepositoryTest {

    @Mock
    MongoTemplate mongoTemplate;

    @InjectMocks
    SequenceRepository repository;

    @Test
    void allocateNextReturnsSequenceNumber() {
        SequenceCounter counter = new SequenceCounter();
        counter.setId("20260707-FICC-I");
        counter.setSeq(3L);
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(SequenceCounter.class)))
                .thenReturn(counter);

        assertThat(repository.allocateNext("20260707-FICC-I")).isEqualTo(3L);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(), any(Update.class), any(), eq(SequenceCounter.class));
        assertThat(queryCaptor.getValue().getQueryObject()).containsEntry("_id", "20260707-FICC-I");
    }

    @Test
    void allocateNextFailsWhenMongoReturnsNull() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(SequenceCounter.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> repository.allocateNext("missing-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-key");
    }
}
