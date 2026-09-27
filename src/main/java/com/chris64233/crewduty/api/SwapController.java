package com.chris64233.crewduty.api;

import com.chris64233.crewduty.api.Dtos.CreateSwapRequest;
import com.chris64233.crewduty.api.Dtos.SwapResponse;
import com.chris64233.crewduty.service.SwapService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/swaps")
public class SwapController {

    private final SwapService swapService;

    public SwapController(SwapService swapService) {
        this.swapService = swapService;
    }

    /** 提交交换方案（幂等）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SwapResponse submit(@Valid @RequestBody CreateSwapRequest request) {
        return swapService.submit(request);
    }

    @GetMapping("/{id}")
    public SwapResponse get(@PathVariable Long id) {
        return swapService.getProposal(id);
    }

    /** 确认交换（幂等）：重新校验双方完整组合，一次事务生效。 */
    @PostMapping("/{id}/confirm")
    public SwapResponse confirm(@PathVariable Long id,
                                @Valid @RequestBody ConfirmRequest request) {
        return swapService.confirm(id, request.idemKey());
    }

    public record ConfirmRequest(@NotBlank String idemKey) {
    }
}
