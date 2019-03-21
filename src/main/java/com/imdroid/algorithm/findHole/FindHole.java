package com.imdroid.algorithm.findHole;

import com.alibaba.fastjson.JSON;
import com.imdroid.enums.AssociateEnum;
import com.imdroid.enums.PointTypeEnum;
import com.imdroid.enums.QuotaEnum;
import com.imdroid.pojo.bo.Const;
import com.imdroid.pojo.bo.Const.HoleDeviation;
import com.imdroid.pojo.bo.Const.Threshold;
import com.imdroid.pojo.bo.Hole;
import com.imdroid.pojo.bo.Point3D;
import com.imdroid.pojo.entity.BlkPoint;
import com.imdroid.pojo.entity.QuotaData;
import com.imdroid.utils.BigDecimalUtil;
import com.imdroid.utils.PointUtil;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class FindHole {

    /**
     *
     * @param allPoints 旋转后的坐标点
     * @param wallUpBound 墙面的上界
     * @param wallDownBound 墙面的下界
     * @return
     */
    public static List<BlkPoint> getWallPoints(List<BlkPoint> allPoints, double wallUpBound, double wallDownBound){
//        System.out.println("上："+wallUpBound+"下：" + wallDownBound);
        List<BlkPoint> wallPoints = new ArrayList<>();
        for (BlkPoint b:allPoints)
        {
            if (b.getZ()<wallUpBound && b.getZ()>wallDownBound)
            {
                wallPoints.add(b);
            }
        }
        return wallPoints;
    }

    /**
     *
     * 找出安全距离内的一圈闭环
     * @param map 按0.1度分好类的点集
     * @param safeThresholdUp 安全距离上界
     * @param safeThresholdLow 安全距离下界
     * @return 闭环
     */
    public static List<BlkPoint> cycle(Map<Double, List<BlkPoint>> map, double safeThresholdUp, double safeThresholdLow){
        List<BlkPoint> datumPoints = new ArrayList<>();
        for (int i=0; i<3601; i++){
            BlkPoint point = new BlkPoint();
            point.setPhi(i/10.0);
            point.setR(0.0);
            datumPoints.add(point);
        }

        List<BlkPoint> nonZero = FindCycle.findRawEdge(map, safeThresholdUp, safeThresholdLow, datumPoints);
//        pointsToFile(nonZero, "D:\\study\\python\\fof\\rawEdge.txt");

        Map<Double, List<BlkPoint>> x = groupBy(nonZero, Const.Coordinate.X, 2);
        Map<Double, List<BlkPoint>> y = groupBy(nonZero, Const.Coordinate.Y, 2);
//        System.out.println(x.keySet().toString());
//        System.out.println(y.keySet().toString());

        double xMax=0.0; double xMin=0.0;
        double yMax=0.0; double yMin=0.0;
        List<Double> xInner = new ArrayList<>();
        List<Double> yInner = new ArrayList<>();
        int xMaxSize = 0, xMaxSize1=0;
        int yMaxSize, yMaxSize1=0;
        for(double key:x.keySet()){
            double m=computeAver(x.get(key),Const.Coordinate.X);
            xInner.add(m);
            if(m<0)
                xMaxSize = ((x.get(key).size() > xMaxSize) ? x.get(key).size() : xMaxSize);
            if(m>0)
                xMaxSize1 = ((x.get(key).size() > xMaxSize1) ? x.get(key).size() : xMaxSize1);
            xMin=(m < xMin ? m: xMin);
            xMax=(m > xMax ? m: xMax);
        }

        for(double key:y.keySet()){
            double m=computeAver(y.get(key),Const.Coordinate.Y);
            yInner.add(m);
            yMin=(m < yMin ? m: yMin);
            yMax=(m > yMax ? m: yMax);
        }



//      修正闭环(最外一圈)
        for (int i=0; i<3601; i++){
            double theta = i/10.0*Math.PI/180;
            if(i==0)
                datumPoints.get(i).set_datumPoint(xMax,0,safeThresholdUp, xMax);
            else if(i==900)
                datumPoints.get(i).set_datumPoint(0,yMax,safeThresholdUp, yMax);
            else if(i==1800)
                datumPoints.get(i).set_datumPoint(xMin,0,safeThresholdUp, Math.abs(xMin));
            else if(i == 2700)
                datumPoints.get(i).set_datumPoint(0,yMin,safeThresholdUp, Math.abs(yMin));
            else if(i<900) {
                setDatumPoint(datumPoints, i, theta, xMax, yMax,safeThresholdUp);
            }
            else if(i<1800){
                setDatumPoint(datumPoints, i, theta, xMin, yMax,safeThresholdUp);
            }
            else if(i<2700){
                setDatumPoint(datumPoints, i, theta-Math.PI, xMin, yMin,safeThresholdUp);
            }
            else
                setDatumPoint(datumPoints, i, theta-Math.PI, xMax, yMin,safeThresholdUp);
        }

//      修正凹进去的部分
        if(x.size()>2 ) {
//            if(x.size() != y.size())
//            {
//                if(x.size()<y.size()){
//
//                }
//            }
            setDatumPoint(datumPoints, x, Const.Coordinate.X, xInner, yMax, yMin, safeThresholdUp);
        }
        if(y.size()>2){
            setDatumPoint(datumPoints, y, Const.Coordinate.Y, yInner, xMax, xMin, safeThresholdUp);
        }

//        pointsToFile(datumPoints, "D:\\study\\python\\fof\\Edge.txt");
//        System.out.println("文件输出完成");
        return datumPoints;
    }

    public static void setOuterPoints(List<BlkPoint> allPoints, List<BlkPoint> datumPoints){
        for(BlkPoint blkPoint:allPoints){
//            double r = datumPoints.get((int)(blkPoint.getPhi()*10)).getR();
            if (isOuterPoint(blkPoint, datumPoints)) {
                blkPoint.setType(PointTypeEnum.OUT_POINT.getCode()); // 飘出点
            }
        }
    }

    public static boolean isOuterPoint(BlkPoint blkPoint, List<BlkPoint> datumPoints){
        double r = datumPoints.get((int)(blkPoint.getPhi()*10)).getR();
        if (blkPoint.getR() - r > Threshold.OUT_POINT_DISTANCE) {
            double x = datumPoints.get((int) (blkPoint.getPhi() * 10)).getX();
            double y = datumPoints.get((int) (blkPoint.getPhi() * 10)).getY();
            double deltaX = blkPoint.getX() - x;
            double deltaY = blkPoint.getY() - y;
            BlkPoint newDatumP1 = new BlkPoint(blkPoint.getX() - deltaX, blkPoint.getY(), blkPoint.getZ());
            PointUtil.setPhi(newDatumP1);
            BlkPoint newDatumP2 = new BlkPoint(blkPoint.getX(), blkPoint.getY() - deltaY, blkPoint.getZ());
            PointUtil.setPhi(newDatumP2);
            double dis1 = PointUtil.getDistance(newDatumP1, datumPoints.get((int) (newDatumP1.getPhi() * 10)));
            double dis2 = PointUtil.getDistance(newDatumP2, datumPoints.get((int) (newDatumP2.getPhi() * 10)));
            double distance = Math.abs(dis1 < dis2 ? deltaX : deltaY);
            return (distance > Threshold.OUT_POINT_DISTANCE);
        }
        return false;
    }

    public static <T extends Point3D> double computeAver(@NonNull List<T> points, String coordinate){
        double sum=0.0;

        if(Const.Coordinate.X.equals(coordinate)){
            for(T point:points){
                sum+=point.getX();
            }
        }
        if(Const.Coordinate.Y.equals(coordinate)){
            for(T point:points){
                sum+=point.getY();
            }
        }
        if(Const.Coordinate.Z.equals(coordinate)){
            for(T point:points){
                sum+=point.getZ();
            }
        }
        return sum/points.size();
    }

    public static <T extends BlkPoint> Map<Double, List<T>> groupBy(@NonNull List<T> points, String coordinate, int scale){
        Map<Double, List<T>> map = new TreeMap<>();
        for (T point : points) {
            double coord = 0d;
            if (Const.Coordinate.X.equals(coordinate)) {
                coord = BigDecimalUtil.scale(point.getX(), scale);
            }
            if (Const.Coordinate.Y.equals(coordinate)) {
                coord = BigDecimalUtil.scale(point.getY(), scale);
            }
            List<T> subList = map.get(coord);
            if (null == subList) {
                subList = new ArrayList<>();
            }
            subList.add(point);
            map.put(coord, subList);
        }

        Map<Double, List<T>> map1 = new TreeMap<>();
        double lastKey = 0.0, map1Key=0.0;
        int flag = 0; int continueFlag = 0;
        double normalSize=0.0;
        if(points.size() > 0 && map.size()>0) {
            normalSize = Math.floor((double)points.size() / (double)map.size())*2;
        }
        for(double key:map.keySet()){
            if(flag == 0)
            {
                map1Key = key; // 第一个数
                flag = 1;
            }
            if(map.get(key).size()>normalSize)
            {
                List<T> subList = map.get(key);
                if(Math.abs(key-lastKey)<0.02 && continueFlag == 1){
                    subList.addAll(map1.get(map1Key));
                    map1.put(map1Key, subList);
                }else {
                    map1.put(key, subList);
                    if(continueFlag == 0)
                        map1Key = key;
                    continueFlag = 1;
                }
                lastKey = key;
            }else{
                continueFlag = 0;
            }
        }
        if(map1.size()>2){// 如果不是边界，会有一个大的缺口
            int i=0;
            Iterator<Double> iterator = map1.keySet().iterator();
            while(iterator.hasNext()){
//            for(double key:map1.keySet()){
                i++;
                double key = iterator.next();
                if(i>1 && i<map1.size()){
//                    System.out.println(key);
                    Collections.sort(map1.get(key), new Comparator<BlkPoint>() {
                        @Override
                        public int compare(BlkPoint o1, BlkPoint o2) {
                            return o1.getPhi().compareTo(o2.getPhi());
                        }
                    });

//                    处理跨一、四象限的情况
                    if(map1.get(key).get(0).getPhi()<90 && map1.get(key).get(map1.get(key).size()-1).getPhi()>270)
                    {
                        int mark = 0;
                        for(T point:map1.get(key)){
                            if(point.getPhi()>270)
                                break;
                            mark++;
                        }
                        List<T> subList = map1.get(key).subList(mark, map1.get(key).size()-1);
                        subList.addAll(map1.get(key).subList(0, mark));
                        map1.put(key, subList);
                    }
                    List<BlkPoint> tmpContinue = new ArrayList<>();
                    double start=0.0;double end=0.0; int size=0;
                    double continueLength = 0.0;
                    for(BlkPoint blkPoint:map1.get(key)){
//                        最大连续
                        if(tmpContinue.isEmpty()){
                            tmpContinue.add(blkPoint);
                            continue;
                        }

                        if(Math.abs(tmpContinue.get(tmpContinue.size()-1).getPhi()-blkPoint.getPhi())<5 ||
                                Math.abs(tmpContinue.get(tmpContinue.size()-1).getPhi()-blkPoint.getPhi())>355)
                        {
                            tmpContinue.add(blkPoint);
                        }
                        else {
                            if(size<tmpContinue.size())
                            {
                                start = tmpContinue.get(0).getPhi();
                                end = tmpContinue.get(tmpContinue.size()-1).getPhi();
                                size = tmpContinue.size();
                                if(Const.Coordinate.X.equals(coordinate))
                                    continueLength = Math.abs(tmpContinue.get(tmpContinue.size()-1).getY()-tmpContinue.get(0).getY());
                                else if(Const.Coordinate.Y.equals(coordinate))
                                    continueLength = Math.abs(tmpContinue.get(tmpContinue.size()-1).getX()-tmpContinue.get(0).getX());
                            }
                            tmpContinue.clear();
                            tmpContinue.add(blkPoint);
                        }

                    }
                    if(!tmpContinue.isEmpty() && size<tmpContinue.size()) {
                        start = tmpContinue.get(0).getPhi();
                        end = tmpContinue.get(tmpContinue.size() - 1).getPhi();
                        if (Const.Coordinate.X.equals(coordinate))
                            continueLength = Math.abs(tmpContinue.get(tmpContinue.size() - 1).getY() - tmpContinue.get(0).getY());
                        else if (Const.Coordinate.Y.equals(coordinate))
                            continueLength = Math.abs(tmpContinue.get(tmpContinue.size() - 1).getX() - tmpContinue.get(0).getX());
                    }
//                    if(Const.Coordinate.X.equals(coordinate))
//                        continueLength = Math.abs(tmpContinue.get(tmpContinue.size()-1).getY()-tmpContinue.get(0).getY());
//                    else if(Const.Coordinate.Y.equals(coordinate))
//                        continueLength = key*(1/Math.tan(start/180*Math.PI)-1/Math.tan(end/180*Math.PI));

                    if(continueLength<0.5)
                    {
                        i--;
                        iterator.remove();
                    }
//                    删除不在start和end范围内的值
                    else
                    {
                        Iterator<T> iter = map1.get(key).iterator();
                        while(iter.hasNext()){
                            BlkPoint blkPoint =  iter.next();
                            if(start > end){
                                if (blkPoint.getPhi() < start && blkPoint.getPhi() > end)
                                    iter.remove();
                            }
                            else
                            {
                                if(blkPoint.getPhi() < start  || blkPoint.getPhi() > end)
                                    iter.remove();
                            }
                        }
//                        System.out.println(start+" "+end);
//                        if(map1.get(key).size()>0)
//                            System.out.println(map1.get(key).get(0).getPhi()+" "+map1.get(key).get(map1.get(key).size()-1).getPhi());

                    }
                }
            }
        }
//        Iterator<Double> iterator = map1.keySet().iterator();
//        while(iterator.hasNext()){
//            double key = iterator.next();
//            if(map1.get(key).size()<100){
//                iterator.remove();
//            }
//        }
        return map1;
    }

    private static void setDatumPoint(List<BlkPoint> datumPoints, int i, double theta, double x, double y, double z){
        double r1 = Math.abs(x/Math.cos(theta));
        double r2 = Math.abs(y/Math.sin(theta));
        if (Math.abs(datumPoints.get(i).getR() - Math.min(r1, r2)) > 0.01) {
            if (r1 < r2) {
                datumPoints.get(i).set_datumPoint(x,x*Math.tan(theta), z, Math.min(r1,r2));
            } else {
                datumPoints.get(i).set_datumPoint(y/Math.tan(theta), y, z, Math.min(r1,r2));
            }
        }
    }


    private static void setDatumPoint(List<BlkPoint> datumPoints, Map<Double, List<BlkPoint>> map, String coordinate, List<Double> Inner, double max, double min, double safeThresholdUp){
        int i=0;
        for(double key:map.keySet()){
            i++;
            if(i>1 && i<map.size()){
                double value = Inner.get(i-1);
                double start = map.get(key).get(0).getPhi();
                double end = map.get(key).get(map.get(key).size()-1).getPhi();
                if(start > end)
                {
                    end+=360;
                    datumPoints.get(3600).set_datumPoint(value, 0,safeThresholdUp, value);
                }
                if(Const.Coordinate.X.equals(coordinate)) {
                    for (double theta = start; theta <= end; theta += 0.1) {
                        double theta1 = BigDecimalUtil.scale(mod360(theta), 1);
                        if (theta1%90<0.01){
                            datumPoints.get((int)(theta1 * 10)).set_datumPoint(value, 0,safeThresholdUp, value);
                        }
                        if (theta1 < 180)
                            setDatumPoint(datumPoints, (int)(theta1 * 10), theta1 * Math.PI / 180, value, max, safeThresholdUp);
                        else
                            setDatumPoint(datumPoints, (int)(theta1 * 10), theta1 * Math.PI / 180 - Math.PI, value, min, safeThresholdUp);
                    }
                }
                else if(Const.Coordinate.Y.equals(coordinate)){
                    for (double theta = start; theta <= end; theta += 0.1) {
                        double theta1 = BigDecimalUtil.scale(mod360(theta), 1);
                        if (theta1%90<0.01){
                            datumPoints.get((int)(theta1 * 10)).set_datumPoint(0,value,safeThresholdUp, value);
                        }
                        if (theta1 < 180)
                            setDatumPoint(datumPoints, (int)(theta1 * 10), theta1 * Math.PI / 180, max, value, safeThresholdUp);
                        else
                            setDatumPoint(datumPoints, (int)(theta1 * 10), theta1 * Math.PI / 180 - Math.PI, min, value, safeThresholdUp);
                    }
                }
            }

        }
    }

    /**
     *
     * @param wallPoints
     * @param datumPoints
     * @param ceiling
     * @param floor
     * @return
     */
    public static List<Double> findHoleMark(Map<Double, List<BlkPoint>> map, List<BlkPoint> datumPoints, double ceiling, double floor) {

//        Map<Double, List<BlkPoint>> hole = new TreeMap<>();
        List<Double> hole = new ArrayList<>();
//        List<BlkPoint> innerWallPoints = new ArrayList<>();
//        for(BlkPoint blkPoint:wallPoints){
//            if(!PointTypeEnum.OUT_POINT.getCode().equals(blkPoint.getType()))
//                innerWallPoints.add(blkPoint);
//        }
//
//        Map<Double, List<BlkPoint>> map = PointUtil.groupByCoordinate(innerWallPoints, Const.Coordinate.PHI);

        int i;
        for (Double key:map.keySet()){
            List<Double> holeZ = new ArrayList<>();
            int z;int countR = 0;
            for(BlkPoint blkPoint:map.get(key)){
                if(!PointTypeEnum.OUT_POINT.getCode().equals(blkPoint.getType()))
                    holeZ.add(blkPoint.getZ());
                else
                    countR++;
            }
            Collections.sort(holeZ);
            double height = 0;//最小值
            for (z = 0; z < holeZ.size() - 1; z++) {
                if (holeZ.get(z + 1) - holeZ.get(z) > height) {
                    height = holeZ.get(z + 1) - holeZ.get(z);
                }
            }
            if(height>Threshold.HOLE_HEIGHT_MIN || countR>0)
                hole.add(key);
//            System.out.println(height+" "+ normalHeight);
//            int size = map.get(key).size();
////            间隔
//            double interval = (ceiling-floor)/size*10;
//            double z = datumPoints.get((int)(key*10)).getZ();
////            System.out.println(key+" "+size+" " + interval+" "+Z+" "+R);
//            int countZ = 0;
//            int countR = 0;
//            for(BlkPoint blkPoint:map.get(key)){
////                有无缺失
//                if(z - blkPoint.getZ()>interval){
//                    z = z-interval;
//                    countZ ++;
//                }
//
//                else {
////                有无突变
//                    if(blkPoint.getZ()<z){
//                        z = blkPoint.getZ();
//                    }
//                    if (isOuterPoint(blkPoint, datumPoints)) {
//                        countR++;
//                    }
//                }
//            }
//            if((countZ>10 && size/countZ>15)||countR>0){
//                hole.add(key);
//            }
        }

//        去除误判点
//        判断hole集合中的key的连续程度
//        若连续的角度所对应的窗口长度超过40cm，则认为是洞口
        Double[] keyHole = new Double[hole.size()];
        keyHole = hole.toArray(keyHole);
        List<Double> holeMark = new ArrayList<>();
        i=0;
        int j = 1;
        while (j<keyHole.length){
            if(keyHole[j]-keyHole[j-1]<0.5){
                j++;
//                如果到了最后一个数
                if(j == keyHole.length){
//                  判定是否满足窗口的条件
                    if(keyHole[j-1]-keyHole[i]<0.5)
                        break;
                    if(Math.abs(datumPoints.get((int)(keyHole[j-1]*10)).getX()-
                            datumPoints.get((int) (keyHole[i] * 10)).getX()) > Threshold.HOLE_WIDTH_MIN ||
                            Math.abs(datumPoints.get((int)(keyHole[j-1]*10)).getY()-
                                    datumPoints.get((int) (keyHole[i] * 10)).getY()) > Threshold.HOLE_WIDTH_MIN ||
                            Math.abs(keyHole[j-1]-360.0)<0.01){
                        holeMark.add(keyHole[i]);
                        holeMark.add(keyHole[j-1]);
                    }
                }
            }

//            有断开
            else if(keyHole[j]-keyHole[j-1]>=0.5 ){
//                如果起点是0度，可能会和360度相接
                if(Math.abs(datumPoints.get((int)(keyHole[j-1]*10)).getX()-
                        datumPoints.get((int) (keyHole[i] * 10)).getX()) < Threshold.HOLE_WIDTH_MIN &&
                        Math.abs(datumPoints.get((int)(keyHole[j-1]*10)).getY()-
                                datumPoints.get((int) (keyHole[i] * 10)).getY()) < Threshold.HOLE_WIDTH_MIN && keyHole[i] > 0.01) {
                    i=j;
                }
                else{
                    holeMark.add(keyHole[i]);
                    holeMark.add(keyHole[j-1]);
                    i=j+1;
                }
                j++;
            }
        }
//~        完成holeMark：两个一组围城封闭图形
        return holeMark;
    }


    public static List<QuotaData> computeHole(Map<Double, List<BlkPoint>> map, List<Double> holeMark, List<BlkPoint> datumPoints, double floor) {
//        找到边缘的点集
//        先确定高，再确定宽
        List<QuotaData> quotaDataList = new ArrayList<>();
        int i;
        int windowNo=1, doorNo=1;
        for (i = 0; i < holeMark.size() / 2; i++) {
            if (holeMark.get(2 * i) > holeMark.get(2 * i + 1) || holeMark.get(2*i)<0.01)
                continue;
//            开始是0，结尾是360，这两组会连城一个洞口
            if(Math.abs(holeMark.get(2*i+1)-360.0) < 0.01 && holeMark.get(0)<0.01){
                holeMark.set(2*i+1, holeMark.get(1)+360.0);
            }
            double holeMarkMiddle = BigDecimalUtil.scale((holeMark.get(2 * i) + holeMark.get(2 * i + 1)) / 2 - 0.5, 1);
            List<Double> holeZ = new ArrayList<>();
            int z;
            for (z = 0; z < 10; z++) {
                if (map.get(mod360(holeMarkMiddle + z * 0.1)) == null)
                    continue;
                for (BlkPoint holemarkmiddle : map.get(mod360(holeMarkMiddle + z * 0.1))) {
                    if (!isOuterPoint(holemarkmiddle, datumPoints))
//                        Math.abs(holemarkmiddle.getR() - datumPoints.get((int) (mod360(holeMarkMiddle + z * 0.1) * 10)).getR()) < Threshold.OUT_POINT_DISTANCE
                        holeZ.add(holemarkmiddle.getZ());
                }
            }
            Collections.sort(holeZ);
            double height = 0;//最小值
            int heightIndex = 0;
            for (z = 0; z < holeZ.size() - 1; z++) {
                if (holeZ.get(z + 1) - holeZ.get(z) > height) {
                    height = holeZ.get(z + 1) - holeZ.get(z);
                    heightIndex = z;
                }
            }

            if(holeZ.size() == 0){
                continue;
            }
            double holeTop, holeBottom;
//            若是门，不存在下限
            if (height < 0.1) {
                holeBottom = floor+0.01;
                holeTop = holeZ.get(0);
            }
            else{
                holeTop = holeZ.get(heightIndex+1);
                holeBottom = holeZ.get(heightIndex);
            }

            height = holeTop - holeBottom + HoleDeviation.HEIGHT_DEVIATION * 2; // 假设上下各有1cm的误差
//            取门洞两边的边缘点集
//            计算窗宽
//            取窗体中间部分的点集
            double midHeight = (holeTop+holeBottom)/2;
            List<BlkPoint> holeXY = new ArrayList<>();
            z=20;
            for(double start = holeMark.get(2*i) - z * 0.1; start <= holeMark.get(2*i+1) + z * 0.1; start+=0.1){
                double start1 = BigDecimalUtil.scale(mod360(start), 1);
                if (map.get(start1) == null)
                    continue;
                for(BlkPoint holeEdge :map.get(start1)){
//                    holeEdge.getR() - datumPoints.get((int) (start1 * 10)).getR() < Threshold.OUT_POINT_DISTANCE
                    if (!isOuterPoint(holeEdge, datumPoints) &&
                            holeEdge.getZ()<midHeight+0.05 && holeEdge.getZ()>midHeight-0.05)
                        holeXY.add(holeEdge);
                }
            }

//            System.out.println(holeX.size());
//            pointsToFile(holeX, "D:\\study\\python\\fof\\test1.txt");


            double holeWidthX = 0.0; double holeWidthY = 0.0;
            int widthIndexX = 0; int widthIndexY = 0;
            for (z = 0; z < holeXY.size() - 1; z++) {
                if (Math.abs(holeXY.get(z + 1).getX() - holeXY.get(z).getX()) > holeWidthX) {
                    holeWidthX = Math.abs(holeXY.get(z + 1).getX() - holeXY.get(z).getX());
                    widthIndexX = z;
                }
                if (Math.abs(holeXY.get(z + 1).getY() - holeXY.get(z).getY()) > holeWidthY) {
                    holeWidthY = Math.abs(holeXY.get(z + 1).getY() - holeXY.get(z).getY());
                    widthIndexY = z;
                }
            }

            double holeWidth=(holeWidthX > holeWidthY ? holeWidthX : holeWidthY);
            int widthIndex = (holeWidthX > holeWidthY ? widthIndexX : widthIndexY);
            String coordinate = (holeWidthX > holeWidthY ? Const.Coordinate.Y : Const.Coordinate.X);
            if (holeWidth < Threshold.HOLE_WIDTH_MIN || height < Threshold.HOLE_HEIGHT_MIN)
                continue;
            if (height / holeWidth < 0.58 || height / holeWidth > 3.2)
                continue;

//            double startW = (holeWidthX > holeWidthY ?
//                    datumPoints.get((int)(holeXY.get(widthIndex).getPhi()*10)).getX() :
//                    datumPoints.get((int)(holeXY.get(widthIndex).getPhi()*10)).getY());
//            double endW = (holeWidthX > holeWidthY ?
//                    datumPoints.get((int)(holeXY.get(widthIndex+1).getPhi()*10)).getX() :
//                    datumPoints.get((int)(holeXY.get(widthIndex+1).getPhi()*10)).getY());
//            double coordinateValue = (holeWidthX > holeWidthY ?
//                    datumPoints.get((int)(holeXY.get(widthIndex+1).getPhi()*10)).getY() :
//                    datumPoints.get((int)(holeXY.get(widthIndex+1).getPhi()*10)).getX());

            double startW = (holeWidthX > holeWidthY ?
                    holeXY.get(widthIndex).getX() :
                    holeXY.get(widthIndex).getY());
            double endW = (holeWidthX > holeWidthY ?
                    holeXY.get(widthIndex+1).getX() :
                    holeXY.get(widthIndex+1).getY());
            double coordinateValue = (holeWidthX > holeWidthY ?
                    holeXY.get(widthIndex+1).getY() :
                    holeXY.get(widthIndex+1).getX());

                    holeMark.set(2 * i, holeXY.get(widthIndex).getPhi());//洞口开始角度
            holeMark.set(2 * i + 1, holeXY.get(widthIndex + 1).getPhi());//洞口结束角度


            height = BigDecimalUtil.scale(height, 4);
            holeWidth = BigDecimalUtil.scale(holeWidth, 4);

            Hole hole = new Hole();
            hole.setHeight(height);
            hole.setWidth(holeWidth);
            hole.setNo("c");
            hole.setCoordinate(coordinate);
            hole.setStartW(startW);
            hole.setEndW(endW);
            hole.setCoordinateValue(coordinateValue);
//            门洞
            if(Math.abs(holeBottom - floor)<0.1){
                // 考虑精度需要多加0.1度
                setHoleType(PointTypeEnum.DOOR_HOLE.getCode(), holeMark, i, datumPoints, map, holeTop, holeBottom);
                //TODO 之后要封装好
//                if (height * holeWidth >= 0.5) {
                    hole.setType("门");
                    hole.setName("" + (doorNo++));
//                }
            }
//            窗洞
            else {
                setHoleType(PointTypeEnum.WINDOW_HOLE.getCode(), holeMark, i, datumPoints, map, holeTop, holeBottom);
//                if (height * holeWidth >= 0.5) {
                    hole.setType("窗");
                    hole.setName("" + (windowNo++));
//                }
            }
            String manuals = JSON.toJSONString(hole);
            QuotaData quotaData = new QuotaData(0D, QuotaEnum.DOOR_HOLE_HEIGHT_SIZE.getCode(), 2L, 2L, AssociateEnum.STATION_DATA.getCode());
            quotaData.setManuals(manuals);
            if (quotaDataList.size() < 4) {
                quotaDataList.add(quotaData);
            }


//            System.out.println("窗" + (i + 1) + ":" + holeMark.get(2 * i) + " " + holeMark.get(2 * i + 1));
//            System.out.println("高:" + height);
//            System.out.println("宽：" + holeWidth);


        }
        return quotaDataList;
    }

    private static double mod360(double a){
        return BigDecimalUtil.scale((a+360.0)%360,1);
    }

    /**
     *
     * @param type 点的类型
     * @param holeMark 标记洞口开始和结束角度数组
     * @param i holeMark的索引
     * @param datumPoints 闭环
     * @param map 按角度分的点集
     * @param holeTop
     * @param holeBottom
     */
    private static void setHoleType(int type, List<Double> holeMark, int i, List<BlkPoint> datumPoints, Map<Double, List<BlkPoint>> map, double holeTop, double holeBottom){
        double end=holeMark.get(2*i+1)+0.1;
        if(holeMark.get(2*i)>holeMark.get(2*i+1))
        {
            end += 360.0;
        }
        for(double start = holeMark.get(2*i) ; start <= end; start+=0.1){
            double start1 = BigDecimalUtil.scale(mod360(start), 1);
            double r = datumPoints.get((int)(start1*10)).getR();
            if (map.get(start1) == null)
                continue;
            for(BlkPoint blkPoint :map.get(start1)){
                if (blkPoint.getZ() < holeTop + HoleDeviation.HEIGHT_DEVIATION &&
                        blkPoint.getZ() > holeBottom - HoleDeviation.HEIGHT_DEVIATION &&
                        !isOuterPoint(blkPoint, datumPoints))
//                    blkPoint.getR() - r < Threshold.OUT_POINT_DISTANCE
                    blkPoint.setType(type);
            }
        }
    }
    /**
     *
     * @param datumPoints 安全区域的闭环
     * @param fileName 文件名
     * @param holeMark 洞口标识
     */
    public static void pointsToFile(List<BlkPoint> datumPoints, String fileName, List<Double> holeMark){
//        写入文件
        FileWriter fw = null;
        try {
            fw = new FileWriter(fileName);
            int i=0;
            for (i=0; i<holeMark.size()/2;i++){
                for(double j=holeMark.get(2*i); j<holeMark.get(2*i+1); j+=0.1){
                    fw.write(datumPoints.get((int)(j*10)).getX()+" "+datumPoints.get((int)(j*10)).getY()+"\r\n");
                }
            }
            fw.flush();


        } catch (FileNotFoundException e) {
//            e.printStackTrace();
            log.error("文件未找到");
        } catch (IOException e) {
            log.error("写文件出错");
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    log.error("写文件出错");
                }
            }
        }
    }

    /**
     *
     * @param Points 输出点集
     * @param fileName 文件名
     */
    public static void pointsToFile(List<BlkPoint> Points, String fileName){
        //        写入文件
        FileWriter fw = null;
        try {
            fw = new FileWriter(fileName);
            int i=0;
            for (BlkPoint blkPoint:Points) {
                fw.write(blkPoint.getX() + " " + blkPoint.getY()+ " "+blkPoint.getZ()+"\r\n");
            }
            fw.flush();
        } catch (FileNotFoundException e) {
//            e.printStackTrace();
            log.error("文件未找到");
        } catch (IOException e) {
            log.error("写文件出错");
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    log.error("写文件出错");
                }
            }
        }
    }

}
