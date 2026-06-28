package com.learning.english.dto;

import java.util.List;

public record ThemeOptionDto(
    String id,
    String name,
    List<String> colors,
    String bgClass,
    String darkBgClass
) {}
