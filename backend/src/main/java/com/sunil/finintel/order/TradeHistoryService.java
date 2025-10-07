package com.sunil.finintel.order;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sunil.finintel.common.NotFoundException;
import com.sunil.finintel.common.PageResponse;
import com.sunil.finintel.user.UserRepository;

@Service
public class TradeHistoryService {

    static final int MAX_PAGE_SIZE = 100;

    private final ExecutionRepository executionRepository;
    private final UserRepository userRepository;

    public TradeHistoryService(ExecutionRepository executionRepository, UserRepository userRepository) {
        this.executionRepository = executionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionView> listForUser(Long userId, int page, int size) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("user " + userId + " not found");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "executedAt", "id"));
        return PageResponse.from(executionRepository.findByUserId(userId, pageable).map(ExecutionView::from));
    }
}
