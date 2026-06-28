package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock HistoryRepository historyRepository;
    @Mock EmploymentRepository employmentRepository;
    @Mock Employment employment;
    @InjectMocks HistoryService historyService;

    HistoryEvent buildMockEvent(LocalDateTime timestamp) {
        return new HistoryEvent(HistoryEvent.EventType.EWA_REQUEST, timestamp, null);
    }

    @Test
    void 첫_페이지_hasNext_true_nextCursor_반환() {
        when(employment.getEmployerId()).thenReturn(1L);
        when(employmentRepository.findById(10L)).thenReturn(Optional.of(employment));
        LocalDateTime t1 = LocalDateTime.of(2024, 1, 1, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2024, 1, 2, 9, 0);
        LocalDateTime t3 = LocalDateTime.of(2024, 1, 3, 9, 0);
        when(historyRepository.getHistory(eq(10L), isNull(), eq(3))).thenReturn(List.of(
                buildMockEvent(t1), buildMockEvent(t2), buildMockEvent(t3)
        ));

        HistoryResponse response = historyService.getHistories(10L, 1L, null, 2);

        assertTrue(response.hasNext());
        assertEquals(t2.toString(), response.nextCursor());
        assertEquals(2, response.events().size());
    }

    @Test
    void 첫_페이지_hasNext_false_nextCursor_null() {
        when(employment.getEmployerId()).thenReturn(1L);
        when(employmentRepository.findById(10L)).thenReturn(Optional.of(employment));
        LocalDateTime t1 = LocalDateTime.of(2024, 1, 1, 9, 0);
        when(historyRepository.getHistory(eq(10L), isNull(), eq(3))).thenReturn(List.of(
                buildMockEvent(t1)
        ));

        HistoryResponse response = historyService.getHistories(10L, 1L, null, 2);

        assertFalse(response.hasNext());
        assertNull(response.nextCursor());
        assertEquals(1, response.events().size());
    }

    @Test
    void 다음_페이지_after_있으면_cursor_변환해서_repository_호출() {
        when(employment.getEmployerId()).thenReturn(1L);
        when(employmentRepository.findById(10L)).thenReturn(Optional.of(employment));
        LocalDateTime cursorTime = LocalDateTime.of(2024, 1, 2, 9, 0);
        String after = cursorTime.toString();
        when(historyRepository.getHistory(eq(10L), eq(Timestamp.valueOf(cursorTime)), eq(3)))
                .thenReturn(List.of());

        historyService.getHistories(10L, 1L, after, 2);

        verify(historyRepository).getHistory(10L, Timestamp.valueOf(cursorTime), 3);
    }

    @Test
    void 고용주_권한_체크() {
        when(employment.getEmployerId()).thenReturn(1L);
        when(employment.getWorkerId()).thenReturn(2L);
        when(employmentRepository.findById(10L)).thenReturn(Optional.of(employment));

        assertThrows(UnauthorizedException.class,
                () -> historyService.getHistories(10L, 99L, null, 20));
    }

    @Test
    void 근로자_권한_체크() {
        when(employment.getEmployerId()).thenReturn(1L);
        when(employment.getWorkerId()).thenReturn(2L);
        when(employmentRepository.findById(10L)).thenReturn(Optional.of(employment));
        when(historyRepository.getHistory(eq(10L), isNull(), eq(21))).thenReturn(List.of());

        assertDoesNotThrow(() -> historyService.getHistories(10L, 2L, null, 20));
    }
}
