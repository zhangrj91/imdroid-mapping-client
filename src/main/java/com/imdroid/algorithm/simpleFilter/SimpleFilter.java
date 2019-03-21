package com.imdroid.algorithm.simpleFilter;

import com.imdroid.enums.PointTypeEnum;
import com.imdroid.pojo.bo.Point3D;
import com.imdroid.pojo.entity.BlkPoint;
import com.imdroid.utils.PointUtil;

import java.util.List;

public class SimpleFilter {
    private double dis;
    public void filter(List<BlkPoint> points_5, List<BlkPoint> points_9, double distance3, String type, boolean isVer) {
        for (BlkPoint point : points_9) {
            if (isNotQualified(points_5, point, distance3)) {
                for (BlkPoint point1 : points_5) {
                    if (point1.getDistance(point) < 1 * dis)
                        if (type.equals("raise"))
                        {
                            if (!isVer)
                                point1.setType(PointTypeEnum.RAISE_FLAT.getCode());
                            else {
                                if(PointTypeEnum.RAISE_FLAT.getCode().equals(point1.getType()))
                                    point1.setType(PointTypeEnum.RAISE.getCode());
                                else
                                    point1.setType(PointTypeEnum.RAISE_VERTICAL.getCode());
                            }
                        } else{
                            if (!isVer)
                                point.setType(PointTypeEnum.SAG_FLAT.getCode());
                            else {
                                if(PointTypeEnum.SAG_FLAT.getCode().equals(point1.getType()))
                                    point1.setType(PointTypeEnum.SAG.getCode());
                                else
                                    point1.setType(PointTypeEnum.SAG_VERTICAL.getCode());
                            }
                        }
                }
            }
        }
    }

    public boolean isNotQualified(List<BlkPoint> list_5, BlkPoint blkPoint, double distance){
        int count = 0;
        dis = PointUtil.getRadiusByDistance(blkPoint, distance);
        for (BlkPoint point : list_5){
            if (point.getDistance(new Point3D(0.0,0.0,0.0)) < 20 * distance){
                count++;
            }
            if (count >= 3)
            {
                return true;
            }
        }
        return false;
    }
}
