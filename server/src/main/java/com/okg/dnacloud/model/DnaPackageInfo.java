package com.okg.dnacloud.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DnaPackageInfo {
    private String id;
    private String name;
    private String version;
    private String domain;
    private String description;
    private String packageType;
    private String objective;
    private List<String> capabilities;
    private List<String> notGuaranteed;
    private DnaPrice price;
    private DnaPayout payout;
    private Integer dnaScore;    // null 表示未评分
    private Boolean certified;   // true=官方认证，false=社区包
}
