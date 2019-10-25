# jmeter-influxdb-listener
Simple backend listener for sending JMeter sampler metrics to InfluxDB.

This listener sends metrics for each sampler invocation to InfluxDB. The existing Apache backend listener for InfluxDB aggregates metrics locally over a relatively short period of time before sending them to influx (e.g. calculates 95% response time across 5 seconds of samples). This method can be less accurate than calculating across a longer period, and some use cases require finer grained data collection.

This listener aims to resolve both of those concerns by simply sending each sampler's metrics and thus allowing processing and aggregation in the presentation layer after influx.

## Usage
Build and copy jar into JMeter's lib/ext directory. Add a backend listener to your test plan and select this implementation in it.
```
gradle clean build
cp build/libs/SimpleInfluxDBBackendListenerClient.jar $JMETER_DIR/lib/ext
```
