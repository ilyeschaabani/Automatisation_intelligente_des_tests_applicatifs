package com.pfe.platform.msexecution.dto;

import com.pfe.platform.msexecution.entity.UxNavigationStep;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UxNavigationStepDto {
    private Long id;
    private Integer stepNumber;
    private String stepName;
    private String actionPerformed;
    private String observation;
    private String pageUrl;
    private String pageTitle;
    private String screenshotBase64;
    /** CLICK | FILL | SCROLL | DONE | SKIP */
    private String actionType;
    /** CSS selector or visible text of the target element */
    private String selector;
    /** Value typed (only for FILL actions) */
    private String fillValue;

    public static UxNavigationStepDto fromEntity(UxNavigationStep e) {
        return UxNavigationStepDto.builder()
                .id(e.getId())
                .stepNumber(e.getStepNumber())
                .stepName(e.getStepName())
                .actionPerformed(e.getActionPerformed())
                .observation(e.getObservation())
                .pageUrl(e.getPageUrl())
                .pageTitle(e.getPageTitle())
                .screenshotBase64(e.getScreenshotBase64())
                .actionType(e.getActionType())
                .selector(e.getSelector())
                .fillValue(e.getFillValue())
                .build();
    }
}
