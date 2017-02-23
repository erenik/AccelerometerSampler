package erenik.accelerometersampler;

import android.content.res.Resources;

import java.util.ArrayList;

/**
 * A frame of time for sensing which is later analyzed.
 * Sensors include magnitude of acc. and gyroscope.
 * Calculations include avg, min, max and stddev on both.
 * Created by Emil on 2017-02-23.
 */

public class SensingFrame {
    ArrayList<MagnitudeData> accMagns = new ArrayList<>(),
        gyroMagns = new ArrayList<>();

    SensingFrame(String transport){
        startTimeMs = System.currentTimeMillis();
        durationMs = 5000;
        transportString = transport;
    }
    // Stored on creation, System.currentTimeMillis();
    long startTimeMs;
    /// Default 5000
    int durationMs;
    float accMin, accMax, accAvg, accStdev,
        gyroMin, gyroMax, gyroAvg, gyroStdev;

    String transportString = "";

    void CalcStats(){
        if (accMagns.size() > 0) {
            accMin = Min(accMagns);
            accMax = Max(accMagns);
            accAvg = Avg(accMagns);
            accStdev = Stddev(accMagns, accAvg);
        }
        if (gyroMagns.size() > 0) {
            gyroMin = Min(gyroMagns);
            gyroMax = Max(gyroMagns);
            gyroAvg = Avg(gyroMagns);
            gyroStdev = Stddev(gyroMagns, gyroAvg);
        }
    }
    void ClearMagnitudeData(){
        accMagns = null;
        gyroMagns = null;
    }

    float Min(ArrayList<MagnitudeData> data){
        float min = data.get(0).magnitude;
        for (int i = 1; i < data.size(); ++i)
            if (data.get(i).magnitude < min)
                min = data.get(i).magnitude;
        return min;
    }

    float Max(ArrayList<MagnitudeData> data){
        float max = data.get(0).magnitude;
        for (int i = 1; i < data.size(); ++i)
            if (data.get(i).magnitude > max)
                max = data.get(i).magnitude;
        return max;
    }
    float Avg(ArrayList<MagnitudeData> data){
        float tot = data.get(0).magnitude;
        for (int i = 1; i < data.size(); ++i)
            tot += data.get(i).magnitude;
        return tot / data.size();
    }
    float Stddev(ArrayList<MagnitudeData> data, float avg){
        int totalDiffs = 0;
        for(int i = 0; i < data.size(); i++){
            float diff = data.get(i).magnitude - avg;
            totalDiffs += diff * diff; // this is the calculation for summing up all the values
        }
        return (float) Math.sqrt(totalDiffs);
    }
    public String toString(){
        return (startTimeMs/1000)+","+accString(",")+","+gyroString(",")+","+transportString+"\n";
    }
    public String accString(String glue) {
        return accMin+glue+accMax+glue+accAvg+glue+accStdev;
    }
    public String gyroString(String glue) {
        return gyroMin+glue+gyroMax+glue+gyroAvg+glue+gyroStdev;
    }

    public static String CSVHeaders() {
        return "startTimeSecond,accMin,accMax,accAvg,accStdev,gyroMin,gyroMax,gyroAvg,gyroStdev,transport";
    }
}
