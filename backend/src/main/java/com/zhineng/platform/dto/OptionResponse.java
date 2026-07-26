package com.zhineng.platform.dto;

import java.util.List;

public record OptionResponse(
        List<String> departments,
        List<String> powerTypes,
        List<String> sourceFiles
) {
}
