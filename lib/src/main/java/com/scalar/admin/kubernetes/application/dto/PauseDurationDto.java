package com.scalar.admin.kubernetes.application.dto;

/**
 * DTO representing the duration of a pause operation.
 *
 * <p>This DTO contains the start and end times of a pause operation as epoch milliseconds. It is
 * used to transfer pause duration data from the application layer to the presentation layer without
 * exposing domain objects.
 *
 * @param startTimeEpochMilli the start time as epoch milliseconds
 * @param endTimeEpochMilli the end time as epoch milliseconds
 */
public record PauseDurationDto(long startTimeEpochMilli, long endTimeEpochMilli) {}
