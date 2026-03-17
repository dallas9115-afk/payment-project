import com.bootcamp.paymentdemo.domain.point.entity.PointTransactionEntity;
import com.bootcamp.paymentdemo.domain.point.entity.PointType;
import com.bootcamp.paymentdemo.domain.point.repository.PointTransactionRepository;
import com.bootcamp.paymentdemo.domain.point.service.PointTransactionService;
import com.bootcamp.paymentdemo.global.error.CommonException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) // Mockito 도구를 사용하겠다고 선언! 🚀
class PointTransactionServiceTest {

    @InjectMocks
    private PointTransactionService pointTransactionService; // 우리가 만든 진짜 서비스 ⚙️

    @Mock
    private PointTransactionRepository pointTransactionRepository; // 가짜 리포지토리 🎭

    @Test
    @DisplayName("잔액보다 많은 포인트를 사용하면 P4001 예외가 발생해야 한다")
    void insufficientBalanceTest1() {
        // 1. Given: 현재 잔액이 500원인 상황을 가짜로 만듭니다.
        PointTransactionEntity lastTx = PointTransactionEntity.builder()
                .balanceAfter(500)
                .build();

        // 리포지토리에서 조회하면 무조건 위 데이터를 반환하도록 설정합니다.
        when(pointTransactionRepository.findFirstByUserIdOrderByCreatedAtDesc(anyLong()))
                .thenReturn(Optional.of(lastTx));

        // 2. When & 3. Then: 1000원을 사용했을 때 예외가 터지는지 확인합니다.
        assertThrows(CommonException.class, () -> {
            pointTransactionService.recordPointHistory(1L, 1L, 1L, PointType.USED, 1000);
        });
    }
}
