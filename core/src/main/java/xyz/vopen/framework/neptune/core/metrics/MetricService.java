package xyz.vopen.framework.neptune.core.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.vopen.framework.neptune.common.configuration.Configuration;
import xyz.vopen.framework.neptune.common.utils.time.Time;
import xyz.vopen.framework.neptune.rpc.RpcEndpoint;
import xyz.vopen.framework.neptune.rpc.RpcService;

import java.util.concurrent.CompletableFuture;

/**
 * {@link MetricService} The MetricQueryService creates a key-value representation of all metrics
 * currently registered with Neptune when queried.
 *
 * @author <a href="mailto:siran0611@gmail.com">Elias.Yao</a>
 * @version ${project.version} - 2020/10/14
 */
public class MetricService extends RpcEndpoint implements MetricServiceGateway {
  private static final Logger LOG = LoggerFactory.getLogger(MetricService.class);

  private static final String METRIC_SERVICE_NAME = "MetricService";

  protected MetricService(RpcService rpcService) {
    super(rpcService);
  }

  /**
   * Start the MetricService actor in the given actor system.
   *
   * @param rpcService The rpcService running MetricService.
   * @return
   */
  public static MetricService createMetricService(final Configuration configuration,final RpcService rpcService) {
    return new MetricService(rpcService);
  }

  public void addMetric() {}

  public void removeMetric() {}

  @Override
  protected CompletableFuture<Void> onStop() {
    return super.onStop();
  }

  @Override
  public CompletableFuture<MetricResult> queryMetrics(Time timeout) {
    return null;
  }
}
