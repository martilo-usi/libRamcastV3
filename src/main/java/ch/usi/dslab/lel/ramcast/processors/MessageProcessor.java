package ch.usi.dslab.lel.ramcast.processors;

import ch.usi.dslab.lel.ramcast.RamcastAgent;
import ch.usi.dslab.lel.ramcast.models.RamcastMessage;
import ch.usi.dslab.lel.ramcast.endpoint.RamcastEndpointGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;

public class MessageProcessor {
  private static final Logger logger = LoggerFactory.getLogger(MessageProcessor.class);

  private Queue<RamcastMessage> processing;
  private RamcastEndpointGroup group;
  private RamcastAgent agent;

  public MessageProcessor(RamcastEndpointGroup group, RamcastAgent agent) {
    this.group = group;
    this.agent = agent;
  }

  public void handleMessage(RamcastMessage message) {
    logger.debug("Handling message {}", message);
  }
}
