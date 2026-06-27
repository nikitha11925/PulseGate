package com.pulsegate.model;

/**
 * Supported job kinds. Each value maps to exactly one {@link com.pulsegate.worker.Worker}
 * implementation via the {@link com.pulsegate.worker.WorkerRegistry}.
 */
public enum JobType {
    EMAIL,
    IMAGE_RESIZE,
    REPORT,
    WEBHOOK
}
