package com.imdroid.pojo.bo;

import lombok.Data;

/**
 * @Description:
 * @Author: iceh
 * @Date: create in 2019-01-14 21:22
 * @Modified By:
 */
@Data
public class Hole {
    private String name;
    private String type;
    private String No;
    private Double width;
    private Double height;

    private Double startW;
    private Double endW;
    private String coordinate; // X或者Y方向
    private Double coordinateValue;

    private double thickness;
    private double verticality;
    private double standardSize;
    private double measureSize;
}
