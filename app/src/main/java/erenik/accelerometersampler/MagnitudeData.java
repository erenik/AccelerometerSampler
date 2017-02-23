package erenik.accelerometersampler;

/**
 * Created by Emil on 2017-02-23.
 */
class MagnitudeData {
    MagnitudeData(float magnitude, long timestamp) {
        this.magnitude = magnitude;
        this.timestamp = timestamp;
    }
    long timestamp = 0;
    float magnitude = 0;
}
