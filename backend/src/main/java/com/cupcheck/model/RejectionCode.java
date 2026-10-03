package com.cupcheck.model;

public enum RejectionCode {
    DIRTY_RIM("杯口不洁"),
    RESIDUE("杯内有残留物"),
    DAMAGED("杯体破损"),
    CAPACITY_INSUFFICIENT("容量不足");

    public final String label;

    RejectionCode(String label) {
        this.label = label;
    }
}
