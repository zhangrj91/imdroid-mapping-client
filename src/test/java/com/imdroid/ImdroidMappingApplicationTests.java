package com.imdroid;

import com.imdroid.algorithm.findHole.FindHole;
import com.imdroid.pojo.bo.*;
import com.imdroid.pojo.dto.StationDataDTO;
import com.imdroid.pojo.entity.BlkPoint;
import com.imdroid.pojo.entity.QuotaData;
import com.imdroid.service.TaskDataService;
import com.imdroid.service.TaskService;
import com.imdroid.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.matchers.Find;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.imdroid.service.impl.TaskServiceImpl.rotate;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ImdroidMappingApplicationTests {
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskDataService taskDataService;

//    @Test
//    public void convexHull() {
//        List<BlkPoint> blkPoints = pointMapper.findPoint();
//        double[][] data = new double[2][blkPoints.size()];
//        for (int i = 0; i < blkPoints.size(); i++) {
//            BlkPoint blkPoint = blkPoints.get(i);
//            data[0][i] = blkPoint.getX();   //x轴
//            data[1][i] = blkPoint.getY();   //y轴
//        }
////        ImageUtil.scatterPlot(data, "G://test.jpg", 1500, 2000);
//    }
//
//    @Test
//    public void lofTest() {
//        java.text.DecimalFormat df = new java.text.DecimalFormat("#.####");
//
//        ArrayList<DataNode> dpoints = new ArrayList<DataNode>();
//
//        List<Point2D> points = pointMapper.findPointByDistance();
//        for (int i = 0; i < points.size(); i++) {
//            dpoints.add(new DataNode(i + "", new double[]{points.get(i).getX(), points.get(i).getY()}));
//        }
//
//        OutlierNodeDetect lof = new OutlierNodeDetect();
//
//        List<DataNode> nodeList = lof.getOutlierNode(dpoints);
//
//        double[][] data = new double[2][points.size()];
//        for (int i = 0; i < nodeList.size(); i++) {
//            if (nodeList.get(i).getLof() < 1) {
//                data[0][i] = nodeList.get(i).getDimension()[0];
//                data[1][i] = nodeList.get(i).getDimension()[1];
//            }
//        }
////        ImageUtil.scatterPlot(data,"G://test2.jpg",1500,2000);
//    }

    @Test
    public void analyzeTest() {
        String folder = Const.Folder.POINT_CLOUD;
//        String folder = "D:\\pointCloud";
        String fileName = "vank3301-zhu"; //20190123-3 pc_20190111182835 2018122856
        String suffix = ".txt";
        File file = new File(folder + "/" + fileName + suffix);

        StationDataDTO stationDataDTO = new StationDataDTO();
        stationDataDTO.setPk(155117840723000001L);
        stationDataDTO.setStationType(111);
        stationDataDTO.setStationAlias("station8");
        stationDataDTO.setActualOrder(8);
        stationDataDTO.setComplete(false);
        taskDataService.saveStationData(stationDataDTO);
        taskService.prepareData(stationDataDTO);

        taskService.analyzeTxt(file, Const.Encoding.GBK);
    }

//    @Test
//    public void analyzeTest() {
//        String folder = Const.Folder.POINT_CLOUD;
//        String fileName = "pc_20190220173534"; //20190123-3 pc_20190111182835 2018122856
//        String suffix = ".txt";
//        File file = new File(folder + "/" + fileName + suffix);
//
//        StationDataDTO stationDataDTO = new StationDataDTO();
//        stationDataDTO.setPk(154856344333700001L);
//        stationDataDTO.setStationType(111);
//        stationDataDTO.setStationAlias("station2");
//        stationDataDTO.setActualOrder(2);
//        stationDataDTO.setComplete(false);
//        taskDataService.saveStationData(stationDataDTO);
//        taskService.prepareData(stationDataDTO);
//
//        taskService.analyzeTxt(file, Const.Encoding.GBK);
//    }

//    @Test
//    public void holeTest() throws Exception{
//        try {
//            String folder = Const.Folder.POINT_CLOUD;
//            String fileName = "pc_20190215154642"; //pc_20190111182835
//            String suffix = ".txt";
//            File file = new File(folder + "/" + fileName + suffix);
//            List<BlkPoint> allPoints = PointUtil.blkPointFromTxt(file, Const.Encoding.GBK);
//            rotate(allPoints);
////            List<Point2D> bottom = PointUtil.reduceDimension(allPoints, Const.Coordinate.Z);
////            List<Double> parameters = LineUtil.lineRansac(bottom, allPoints.size() / 10);
////            double deflectionAngleArc = Math.atan(-parameters.get(1) / parameters.get(0));
////            //将全部点进行旋转坐标系
////            PointUtil.rotateHorizontally(allPoints, deflectionAngleArc);
////            System.out.println("水平旋转完成，旋转了" + deflectionAngleArc/Math.PI*180 + "度");
////            FindHole.pointsToFile(allPoints, "D:\\study\\python\\fof\\windowsPoints.txt");
//
//            Map<Double, List<BlkPoint>> map1 = PointUtil.groupByCoordinate(allPoints, Const.Coordinate.Z);
//            int ceilingSize = 0, floorSize = 0; double ceilingKey = 0, floorKey = 0;
//            for(double key:map1.keySet()) {
//                int pointNum = map1.get(key).size();
//                if (map1.get(key).get(0).getZ() < 0 && pointNum > floorSize) {
//                    floorSize = pointNum;
//                    floorKey = key;
//                } else if (map1.get(key).get(0).getZ() > 0 && pointNum > ceilingSize) {
//                    ceilingSize = pointNum;
//                    ceilingKey = key;
//                }
//            }
//            double ceiling = FindHole.computeAver(map1.get(ceilingKey), Const.Coordinate.Z);
//            double floor = FindHole.computeAver(map1.get(floorKey), Const.Coordinate.Z);
//
//
////        取墙面数据点
//            List<BlkPoint> wallPoints = FindHole.getWallPoints(allPoints, ceiling - Const.Threshold.CEILING_THRESHOLD, floor+ Const.Threshold.FLOOR_THRESHOLD);
//            //根据坐标轴对点集分组
//            Map<Double, List<BlkPoint>> map = PointUtil.groupByCoordinate(wallPoints, Const.Coordinate.PHI);
//
//
//            List<BlkPoint> datumPoints = FindHole.cycle(map, ceiling - Const.Threshold.SAFE_THRESHOLD_UP, ceiling - Const.Threshold.SAFE_THRESHOLD_LOW);
//
////            标记飘出点
//            FindHole.setOuterPoints(allPoints, datumPoints);
//            FindHole.pointsToFile(datumPoints, "D:\\study\\python\\fof\\datumPoints.txt");
//
//
//            List<Double> holeMark = FindHole.findHoleMark(map, datumPoints, ceiling, floor);
//            List<QuotaData> quotaDataList = FindHole.computeHole(map, holeMark, datumPoints, floor);
//            for (QuotaData quotaData : quotaDataList) {
//                log.info(quotaData.toString());
//                System.out.println(quotaData.toString());
//            }
//            List<BlkPoint> outerPoints = new ArrayList<>();
//            List<BlkPoint> doorsPoints = new ArrayList<>();
//            List<BlkPoint> windowsPoints = new ArrayList<>();
//            for(double key:map.keySet()){
//                for(BlkPoint blkPoint:map.get(key)){
//                    if(blkPoint.getType()==6)
//                        outerPoints.add(blkPoint);
//                    if(blkPoint.getType()==4)
//                        doorsPoints.add(blkPoint);
//                    if(blkPoint.getType()==5)
//                        windowsPoints.add(blkPoint);
//                }
//            }
//            FindHole.pointsToFile(windowsPoints, "D:\\study\\python\\fof\\windowsPoints.txt");
//            FindHole.pointsToFile(doorsPoints, "D:\\study\\python\\fof\\doorsPoints.txt");
//            FindHole.pointsToFile(outerPoints, "D:\\study\\python\\fof\\outerPoints.txt");
//        }catch (Exception e)
//        {
//            System.out.println(e.toString());
//        }
//
//    }

//    @Test
//    public void planeTest() throws Exception {
//        String folder = "C:\\Mapping";
//        String fileName = "zhu1";
//        String suffix = ".txt";
//        File file = new File(folder + "\\" + fileName + suffix);
//        List<BlkPoint> points = PointUtil.blkPointFromTxt(file, Const.Encoding.GBK, 6);
//        Wall wall = new Wall();
//        wall.setPoints(points);
//        wall.setAxis(Const.Axis.NEGATIVE);
//        wall.setCoordinate(Const.Coordinate.X);
//
//        WallUtil.findBound(wall);
////        Plane optimalPlane = PlaneUtil.getOptimalPlane(wall);
////        System.out.println(optimalPlane);
//    }

//@Test
//public void uploadTest() {
//    File dataFolder = new File(Const.Folder.DATA);
//    File[] dataFiles = dataFolder.listFiles();
//    String url = Const.ServerAddress.MAPPING + "/taskData/uploadResult.do";
//    if (null != dataFiles && dataFiles.length > 0) {
//        for (File data : dataFiles) {
//            boolean isUploadSuccess = HttpClientUtil.uploadFile(url, data);
//        }
//    }

//}

}
