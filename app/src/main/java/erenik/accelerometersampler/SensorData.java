package erenik.accelerometersampler;

/**
 * Created by Emil on 2017-02-23.
 */
class SensorData {
    // Vector length of the data.
    float VectorLength() {
        return (float) Math.sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2]);
    }
    ;
    long timestamp = 0;
    float[] values = new float[3];
}
