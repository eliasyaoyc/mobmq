package xyz.vopen.framework.neptune.core.rpc;

/**
 * {@link StartStoppable} Interface to start and stop the processing of rpc calls in the rpc server.
 *
 * @author <a href="mailto:siran0611@gmail.com">Elias.Yao</a>
 * @version ${project.version} - 2020/10/7
 */
public interface StartStoppable {

  /** Starts the processing of remote procedure calls. */
  void start();

  /** Stops the processing of remote procedure calls. */
  void stop();
}
