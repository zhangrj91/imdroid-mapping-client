package com.imdroid.utils;

import com.imdroid.algorithm.kmeans.Kmeans;
import com.imdroid.algorithm.findHole.FindHole;
import com.imdroid.algorithm.simpleFilter.SimpleFilter;
import com.imdroid.enums.AssociateEnum;
import com.imdroid.enums.PointTypeEnum;
import com.imdroid.enums.QuotaEnum;
import com.imdroid.pojo.bo.*;
import com.imdroid.pojo.bo.Const.Axis;
import com.imdroid.pojo.bo.Const.Coordinate;
import com.imdroid.pojo.bo.Const.PermitDeviation;
import com.imdroid.pojo.bo.Const.PlaneName;
import com.imdroid.pojo.dto.WallDataDTO;
import com.imdroid.pojo.entity.BlkPoint;
import com.imdroid.pojo.entity.QuotaData;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @Description: 墙面工具类
 * @Author: iceh
 * @Date: create in 2018-10-31 11:57
 * @Modified By:
 */
@Slf4j
public class WallUtil {
    /**
     * 获取对应的高度宽度
     *
     * @param wall
     */
    public static void findBound(@NonNull Wall wall) {
        List<BlkPoint> points = wall.getPoints();
        //初始赋值
        double xMax = points.get(0).getX(), xMin = xMax;
        double yMax = points.get(0).getY(), yMin = yMax;
        double zMax = points.get(0).getZ(), zMin = zMax;
        //TODO 后续可根据坐标轴向优化
        for (BlkPoint point : points) {
            double currentX = point.getX();
            double currentY = point.getY();
            double currentZ = point.getZ();
            if (xMax < currentX) {
                xMax = currentX;
            }
            if (xMin > currentX) {
                xMin = currentX;
            }
            if (yMax < currentY) {
                yMax = currentY;
            }
            if (yMin > currentY) {
                yMin = currentY;
            }
            if (zMax < currentZ) {
                zMax = currentZ;
            }
            if (zMin > currentZ) {
                zMin = currentZ;
            }
        }
        double[][] bound = {
                {xMin, xMax},
                {yMin, yMax},
                {zMin, zMax}};
        wall.setBound(bound);
    }

    /**
     * 转换wall到wallData
     *
     * @param wall
     * @return
     */
    public static WallDataDTO formatWall(@NonNull Wall wall) {
        log.info("开始计算墙面" + wall.getName());
        //对图片进行视觉上的校正
        WallUtil.symmetry(wall);
        //填充墙面所需数据
        WallUtil.fillWallQuota(wall);
        //需要先设好平面，因为后续的计算需要
        WallUtil.formatPointType(wall);
        //算出墙中点的边界
        WallUtil.findBound(wall);

        WallDataDTO wallDataDTO = new WallDataDTO();
        BeanUtils.copyProperties(wall, wallDataDTO);
        Long totalPoints = (long) wall.getPoints().size();
        wallDataDTO.setTotalPoints(totalPoints);
        wallDataDTO.setFormula(wall.getPlane().getFormula());
        return wallDataDTO;
    }

    /**
     * 根据wall里的point，填充wall的指标与展示所需数据集
     *
     * @param wall
     */
    public static void fillWallQuota(@NonNull Wall wall) {
        String coordinate = wall.getCoordinate();
        //根据墙中的点，做拟合得到点对应平面函数
        List<BlkPoint> points = wall.getPoints();
//        List<BlkPoint> filterPoints = Kmeans.filter(points);
//        List<DataNode> nodeList = PointUtil.lofNodeList(filterPoints);
//        for (int i = 0; i < nodeList.size(); i++) {
//            if (nodeList.get(i).getLof() > 0.8) {
//                BlkPoint temp = new BlkPoint(nodeList.get(i).getDimension()[0],nodeList.get(i).getDimension()[1],nodeList.get(i).getDimension()[2]);
//                points.remove(temp);
//            }
//        }
//        List<Point3D> point3DS = new ArrayList<>();
//        for (int i = 0; i < points.size(); i++){
//            Point3D point3D = points.get(i);
//            point3DS.add(point3D);
//        }

//        Plane plane = PlaneUtil.planeRansac(point3DS, point3DS.size() / 10);

        Plane plane = PlaneUtil.planeFitting(points);
//        Plane targetPlane;
        Plane targetPlane = PlaneUtil.optimalPlane(points, coordinate);
        Vector3D normalVector = plane.getNormalVector();
        double angle = normalVector.getAngle(new Vector3D(0, 0, 1));
        //此处合格点数是写死的，之后要改成要某种标准计算
        Long detectionPoints = (long) points.size();
        Long passPoints = Math.round(0.8 * detectionPoints);
        //设置指标
        Integer quotaType;
        if (Coordinate.Z.equals(coordinate)) {
            quotaType = QuotaEnum.LEVELNESS.getCode();
        } else {
            quotaType = QuotaEnum.VERTICAL.getCode();
        }
        QuotaData quotaData = new QuotaData(angle, quotaType, detectionPoints, passPoints, AssociateEnum.WALL_DATA.getCode());
        wall.setPlane(plane);
        wall.setTargetPlane(targetPlane);
        wall.addQuotaData(quotaData);

    }

    private static void formatPointType(@NonNull Wall wall) {
        //根据墙面函数对点分类
        List<BlkPoint> qualified_5 = new ArrayList<>();
        List<BlkPoint> raise_5 = new ArrayList<>();
        List<BlkPoint> sag_5 = new ArrayList<>();
        List<BlkPoint> points = wall.getPoints();

        List<BlkPoint> qualified_9 = new ArrayList<>();
        List<BlkPoint> raise_9 = new ArrayList<>();
        List<BlkPoint> sag_9 = new ArrayList<>();

        String coordinate = wall.getCoordinate();
        Map<Double, List<BlkPoint>> map = new TreeMap<>();
        switch (coordinate){
            case Coordinate.X:
                map = PointUtil.groupByCoordinate(points, Coordinate.DIS_TO_X_AXIS);
                break;
            case Coordinate.Y:
                map = PointUtil.groupByCoordinate(points, Coordinate.DIS_TO_Y_AXIS);
                break;
            case  Coordinate.Z:
                map = PointUtil.groupByCoordinate(points, Coordinate.DIS_TO_Z_AXIS);
                break;
        }

        for(double key:map.keySet()) {
            List<BlkPoint> subPoints = map.get(key);
            Plane subPlane = PlaneUtil.planeFitting(subPoints);
            for (BlkPoint blkPoint : subPoints) {
                if (PointTypeEnum.BASIS.getCode().equals(blkPoint.getType())) {
                    //天花的指标做特殊处理
                    double quota = PermitDeviation.SMALL;
                    if (null != wall.getName() && wall.getName().equals(PlaneName.CEILING)) {
                        quota = PermitDeviation.LARGE;
                    }

                    double distance1 = subPlane.getDistance(blkPoint);
                    if (distance1 <= quota) {
                        blkPoint.setType(PointTypeEnum.QUALIFIED.getCode());
                        qualified_5.add(blkPoint);
                    } else {
                        Plane zeroPlane = new Plane(subPlane.getNormalVector(), 0);
                        double distance2 = zeroPlane.getDistance(blkPoint);
                        boolean isSag = Math.abs(subPlane.getIntercept()) + quota < distance2;
                        boolean isRaise = Math.abs(subPlane.getIntercept()) - quota > distance2;

                        if (isSag) {
                            sag_5.add(blkPoint);
                        }
                        if (isRaise) {
                            raise_5.add(blkPoint);
                        }
                    }

                    if (distance1 <= 0.008) {
                        qualified_9.add(blkPoint);
                    } else {
                        Plane zeroPlane = new Plane(subPlane.getNormalVector(), 0);
                        double distance2 = zeroPlane.getDistance(blkPoint);
                        boolean isSag = Math.abs(subPlane.getIntercept()) + 0.008 < distance2;
                        boolean isRaise = Math.abs(subPlane.getIntercept()) - 0.008 > distance2;
                        if (isSag) {
                            sag_9.add(blkPoint);
                        }
                        if (isRaise) {
                            raise_9.add(blkPoint);
                        }
                    }
                }
            }
        }



//
//        Plane plane = wall.getPlane();
//
//        for (BlkPoint blkPoint : points) {
//            if (PointTypeEnum.BASIS.getCode().equals(blkPoint.getType())) {
//                //天花的指标做特殊处理
//                double quota = PermitDeviation.SMALL;
//                if (null != wall.getName() && wall.getName().equals(PlaneName.CEILING)) {
//                    quota = PermitDeviation.LARGE;
//                }
//
//                double distance1 = plane.getDistance(blkPoint);
//
//                if (distance1 <= quota) {
//                    blkPoint.setType(PointTypeEnum.QUALIFIED.getCode());
//                    qualified_5.add(blkPoint);
//                } else {
//                    Plane zeroPlane = new Plane(plane.getNormalVector(), 0);
//                    double distance2 = zeroPlane.getDistance(blkPoint);
//                    boolean isSag = Math.abs(plane.getIntercept()) + quota < distance2;
//                    boolean isRaise = Math.abs(plane.getIntercept()) - quota > distance2;
//
//                    if (isSag) {
//                        sag_5.add(blkPoint);
//                    }
//                    if (isRaise) {
//                        raise_5.add(blkPoint);
//                    }
//                }
//
//                if (distance1 <= 0.008) {
//                    qualified_9.add(blkPoint);
//                } else {
//                    Plane zeroPlane = new Plane(plane.getNormalVector(), 0);
//                    double distance2 = zeroPlane.getDistance(blkPoint);
//                    boolean isSag = Math.abs(plane.getIntercept()) + 0.008 < distance2;
//                    boolean isRaise = Math.abs(plane.getIntercept()) - 0.008 > distance2;
//                    if (isSag) {
//                        sag_9.add(blkPoint);
//                    }
//                    if (isRaise) {
//                        raise_9.add(blkPoint);
//                    }
//                }
//            }
//        }
        double distance3 = wall.getPlane().getDistance(new Point3D(0.0, 0.0, 0.0));
        SimpleFilter simpleFilter = new SimpleFilter();
        simpleFilter.filter(raise_5,raise_9,distance3, "raise", false);
        simpleFilter.filter(sag_5, sag_9, distance3, "sag", false);

        chooseNotQualified_v(qualified_5, raise_9, sag_9, wall);

        //计算出平整度
        double flatness = (double) qualified_5.size() / points.size();
        Long detectionPoints = (long) points.size();
        Long passPoints = (long) qualified_5.size();
        if (null != wall.getName() && wall.getName().equals(PlaneName.CEILING)) {
            QuotaData quotaData = new QuotaData(flatness, QuotaEnum.CEIL_FLATNESS.getCode(), detectionPoints, passPoints, AssociateEnum.WALL_DATA.getCode());
            wall.addQuotaData(quotaData);
        }
        else if (null != wall.getName() && wall.getName().equals(PlaneName.FLOOR)) {
            QuotaData quotaData = new QuotaData(flatness, QuotaEnum.FLOOR_FLATNESS.getCode(), detectionPoints, passPoints, AssociateEnum.WALL_DATA.getCode());
            wall.addQuotaData(quotaData);
        }
        else{
            QuotaData quotaData = new QuotaData(flatness, QuotaEnum.FLATNESS.getCode(), detectionPoints, passPoints, AssociateEnum.WALL_DATA.getCode());
            wall.addQuotaData(quotaData);
        }
   }

    public static void chooseNotQualified_v(List<BlkPoint> points, List<BlkPoint> raise_9, List<BlkPoint> sag_9, Wall wall){
        List<BlkPoint> result_Sag = new ArrayList<>();
        List<BlkPoint> result_Raise = new ArrayList<>();

        List<BlkPoint> result_Sag_8 = new ArrayList<>();
        List<BlkPoint> result_Raise_8 = new ArrayList<>();

        String coordinate = wall.getCoordinate();
        Map<Double, List<BlkPoint>> map = new TreeMap<>();
        switch (coordinate){
            case Coordinate.X:
                map = PointUtil.groupByCoordinate(points, Coordinate.DIS_TO_X_AXIS);
                break;
            case Coordinate.Y:
                map = PointUtil.groupByCoordinate(points, Coordinate.DIS_TO_Y_AXIS);
                break;
            case  Coordinate.Z:
                map = PointUtil.groupByCoordinate(points, Coordinate.DIS_TO_Z_AXIS);
                break;
        }

        for(double key:map.keySet()) {
            List<BlkPoint> subPoints = map.get(key);
            Plane subPlane = PlaneUtil.optimalPlane(subPoints, coordinate);
            for (BlkPoint point:subPoints){
                double quota = PermitDeviation.SMALL;
                if (null != wall.getName() && wall.getName().equals(PlaneName.CEILING)) {
                    continue;
                }
                double distance = subPlane.getDistance(point);

                if (distance <= quota) {
                    point.setType(PointTypeEnum.QUALIFIED.getCode());
                } else {
                    Plane zeroPlane = new Plane(subPlane.getNormalVector(), 0);
                    double distance2 = zeroPlane.getDistance(point);
                    boolean isSag_v = Math.abs(subPlane.getIntercept()) + quota < distance2;
                    boolean isRaise_v = Math.abs(subPlane.getIntercept()) - quota > distance2;

                    if (isSag_v) {
                        result_Sag.add(point);
                    }
                    if (isRaise_v) {
                        result_Raise.add(point);
                    }
                }

                if (distance > 0.006){
                    Plane zeroPlane = new Plane(subPlane.getNormalVector(), 0);
                    double distance2 = zeroPlane.getDistance(point);
                    boolean isSag_v = Math.abs(subPlane.getIntercept()) + 0.006 < distance2;
                    boolean isRaise_v = Math.abs(subPlane.getIntercept()) - 0.006 > distance2;

                    if (isSag_v) {
                        result_Sag_8.add(point);
                    }
                    if (isRaise_v) {
                        result_Raise_8.add(point);
                    }
                }
            }
        }

        Plane plane = wall.getTargetPlane();
//        for (BlkPoint point:points){
//            double quota = PermitDeviation.SMALL;
//            if (null != wall.getName() && wall.getName().equals(PlaneName.CEILING)) {
//                continue;
//            }
//            double distance = plane.getDistance(point);
//
//            if (distance <= quota) {
//                point.setType(PointTypeEnum.QUALIFIED.getCode());
//            } else {
//                Plane zeroPlane = new Plane(plane.getNormalVector(), 0);
//                double distance2 = zeroPlane.getDistance(point);
//                boolean isSag_v = Math.abs(plane.getIntercept()) + quota < distance2;
//                boolean isRaise_v = Math.abs(plane.getIntercept()) - quota > distance2;
//
//                if (isSag_v) {
//                    result_Sag.add(point);
//                }
//                if (isRaise_v) {
//                    result_Raise.add(point);
//                }
//            }
//
//            if (distance > 0.006){
//                Plane zeroPlane = new Plane(plane.getNormalVector(), 0);
//                double distance2 = zeroPlane.getDistance(point);
//                boolean isSag_v = Math.abs(plane.getIntercept()) + 0.006 < distance2;
//                boolean isRaise_v = Math.abs(plane.getIntercept()) - 0.006 > distance2;
//
//                if (isSag_v) {
//                    result_Sag_8.add(point);
//                }
//                if (isRaise_v) {
//                    result_Raise_8.add(point);
//                }
//            }
//        }
        SimpleFilter simpleFilter = new SimpleFilter();
        simpleFilter.filter(result_Raise, result_Raise_8, plane.getDistance(new Point3D(0.0, 0.0, 0.0)), "raise", true);
        simpleFilter.filter(result_Sag, result_Sag_8, plane.getDistance(new Point3D(0.0, 0.0, 0.0)), "sag", true);

        //计算垂直度的合格率
        
    }

    public static void symmetry(@NonNull Wall wall) {
        String axis = wall.getAxis();
        String coordinate = wall.getCoordinate();
        List<BlkPoint> pointData = wall.getPoints();
        if (Coordinate.Z.equals(coordinate) && Axis.POSITIVE.equals(axis)) {
            for (Point3D point3D : pointData) {
                point3D.setZ(-point3D.getZ());
            }
        } else if (Coordinate.X.equals(coordinate) && Axis.POSITIVE.equals(axis)) {
            for (Point3D point3D : pointData) {
                //以z轴为对称轴
                point3D.setX(-point3D.getX());
                point3D.setY(-point3D.getY());
            }
        } else if (Coordinate.Y.equals(coordinate) && Axis.NEGATIVE.equals(axis)) {
            for (Point3D point3D : pointData) {
                //以z轴为对称轴
                point3D.setX(-point3D.getX());
                point3D.setY(-point3D.getY());
            }
        }
    }

}
