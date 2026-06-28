package com.learning.english.dto;

import java.util.List;

public record AppConfigDto(
    String themeColor,
    String themeMode,
    List<ThemeOptionDto> availableThemes
) {}
