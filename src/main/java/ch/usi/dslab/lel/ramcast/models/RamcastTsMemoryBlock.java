package ch.usi.dslab.lel.ramcast.models;

import ch.usi.dslab.lel.ramcast.RamcastConfig;
import ch.usi.dslab.lel.ramcast.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

public class RamcastTsMemoryBlock extends RamcastMemoryBlock {
  //  private RamcastNode node;

  //  |          n1 (0)       |           n2 (1)      |          n1 (2)       |          n2 (3)
  //  |
  //  | s1  |  s2 |  s3 |  s4 |  s1 |  s2 |  s3 |  s4 |  s1 |  s2 |  s3 |  s4 |  s1 |  s2 |  s3 |
  // s4 |

  //  |       s       |
  //  | g1    |    g2 |
  //  |b,#,v,s|b,#,v,s|
  //  b: ballot, sequence, clock, status(p:pending, d: delivered)

  protected static final Logger logger = LoggerFactory.getLogger(RamcastTsMemoryBlock.class);

  public RamcastTsMemoryBlock(
          RamcastNode node, long address, int lkey, int capacity, ByteBuffer buffer) {
    super(address, lkey, capacity, buffer);
    //    this.node = node;
  }

  public RamcastTsMemoryBlock(long address, int lkey, int capacity, ByteBuffer buffer) {
    super(address, lkey, capacity, buffer);
  }

  int getTimestampBallot(int offset, int groupIndex) {
    return 0;
  }

  // return offset of a slot #
  public int getSlotOffset(int slot) {
    return RamcastGroup.getGroupCount() * RamcastConfig.SIZE_TIMESTAMP * slot;
  }

  // return offset of a group with groupIndex in a slot # (there are many groups in one slot, and
  // many slot in one buffer)
  public int getGroupOffsetOfSlot(int slot, int groupIndex) {
    return getSlotOffset(slot) + groupIndex * RamcastConfig.SIZE_TIMESTAMP;
  }

  public void writeLocalTs(
          RamcastMessage message,
          int groupIndex,
          int ballotNumber,
          int sequenceNumber,
          int localClock) {
    assert getBuffer() != null;
    int nodeOffset = getNodeOffset(message.getSource());
    int position =
            nodeOffset
                    + getSlotOffset(message.getGroupSlot(groupIndex))
                    + groupIndex * RamcastConfig.SIZE_TIMESTAMP;
    if (RamcastConfig.LOG_ENABLED)
      logger.debug(
              "[{}] Writing to slot {} index {} ballot {} sequence {} clock {} position {}",
              message.getId(),
              message.getGroupSlot(groupIndex),
              groupIndex,
              ballotNumber,
              sequenceNumber,
              localClock,
              position
//              this.toString()
      );
    this.getBuffer().putInt(position, ballotNumber);
    this.getBuffer().putInt(position + 4, sequenceNumber);
    this.getBuffer().putInt(position + 8, localClock);
//    if (RamcastConfig.LOG_ENABLED)
//      logger.debug(
//              "[{}] After writing to slot {} index {} ballot {} sequence {} clock {} position {}: [{}/{}/{}] {}",
//              message.getId(),
//              message.getGroupSlot(groupIndex),
//              groupIndex,
//              ballotNumber,
//              sequenceNumber,
//              localClock,
//              position,
//              this.getBuffer().getInt(position),
//              this.getBuffer().getInt(position + 4),
//              this.getBuffer().getInt(position + 8),
//              this.toString()
//      );
  }

  //  // FUO is the last 4 bytes of the buffer
  //  public int getFUO() {
  //    assert getBuffer() != null;
  //    return ((ByteBuffer) getBuffer().clear())
  //        .getInt(
  //            RamcastGroup.getGroupCount()
  //                * RamcastConfig.SIZE_TIMESTAMP
  //                * RamcastConfig.getInstance().getQueueLength());
  //  }

  public int getNodeOffset(RamcastNode node) {
    return node.getOrderId()
            * (RamcastConfig.SIZE_TIMESTAMP
            * RamcastGroup.getGroupCount()
            * RamcastConfig.getInstance().getQueueLength()
            + RamcastConfig.SIZE_FUO);
    //    return node.getGroupId()
    //            * RamcastConfig.getInstance().getNodePerGroup()
    //            * (RamcastConfig.SIZE_TIMESTAMP * RamcastConfig.getInstance().getQueueLength()
    //                + RamcastConfig.SIZE_FUO)
    //        + node.getNodeId()
    //            * (RamcastConfig.SIZE_TIMESTAMP * RamcastConfig.getInstance().getQueueLength()
    //                + RamcastConfig.SIZE_FUO);
  }

  @Override
  public String toString() {
    StringBuilder ret = new StringBuilder();
    List<RamcastNode> nodes = RamcastGroup.getAllNodes();

    for (RamcastNode node : nodes) {
      int nodeOffset = getNodeOffset(node);
      for (int i = 0; i < RamcastConfig.getInstance().getQueueLength(); i++) {
        for (int g = 0; g < RamcastGroup.getGroupCount(); g++) {
          int offset = nodeOffset + getSlotOffset(i) + g * RamcastConfig.SIZE_TIMESTAMP;
          byte status = this.getBuffer().get(offset + 12);
          if (RamcastConfig.LOG_ENABLED)
            logger.trace(
                    "printing ts block node {}, slot {} node offset {} offset {}, buffer {}",
                    node,
                    i,
                    nodeOffset,
                    offset,
                    getBuffer());
          ret.append(this.getBuffer().getInt(offset))
                  .append("|")
                  .append(this.getBuffer().getInt(offset + 4))
                  .append("|")
                  .append(this.getBuffer().getInt(offset + 8))
                  .append("|")
                  .append(status == 1 ? 'd' : 'p');
          if (g != RamcastGroup.getGroupCount() - 1) ret.append("│");
        }
        if (i != RamcastConfig.getInstance().getQueueLength() - 1) ret.append("║");
      }
      if (nodes.indexOf(node) != RamcastGroup.getTotalNodeCount() - 1) ret.append("║║");
    }
    //    ret.append("║").append(this.getFUO());
    return StringUtils.formatMessage(ret.toString());
  }

  // for getting absolute address for writing timestamp to a remote node
  public long getNodeTimestampAddress(RamcastMessage message, int groupIndex) {
    return getNodeTimestampAddress(
            message.getGroupSlot(groupIndex), message.getSource(), groupIndex);
  }

  public long getNodeTimestampAddress(int slot, RamcastNode node, int groupIndex) {
    assert this.getAddress() != 0;
    int nodeOffset = getNodeOffset(node);
    int position = getSlotOffset(slot) + groupIndex * RamcastConfig.SIZE_TIMESTAMP;
    if (RamcastConfig.LOG_ENABLED)
      logger.trace(
              "offset for slot {} groupIndex {} is {}, node offset {}",
              slot,
              groupIndex,
              position,
              nodeOffset);
    return this.getAddress() + nodeOffset + position;
  }

  public int getTimestampOffset(RamcastMessage message, int groupIndex) {
    int nodeOffset = getNodeOffset(message.getSource());
    return nodeOffset
            + getSlotOffset(message.getGroupSlot(groupIndex))
            + groupIndex * RamcastConfig.SIZE_TIMESTAMP;
  }

  //  public ByteBuffer getTimestampBufferOfGroup(RamcastMessage message, int groupIndex) {
  //    assert getBuffer() != null;
  //    int nodeOffset = getNodeOffset(message.getSource());
  //    int position =
  //        nodeOffset
  //            + getSlotOffset(message.getGroupSlot(groupIndex))
  //            + groupIndex * RamcastConfig.SIZE_TIMESTAMP;
  //    if (RamcastConfig.LOG_ENABLED)
  //      logger.debug(
  //          "[{}] getTimestampBufferOfGroup for groupIndex {} node {} node offset {} is {}->{}",
  //          message.getId(),
  //          groupIndex,
  //          message.getSource(),
  //          nodeOffset,
  //          position,
  //          position + RamcastConfig.SIZE_TIMESTAMP);
  //    ByteBuffer ret =
  //        ((ByteBuffer)
  //                getBuffer()
  //                    .clear()
  //                    .position(position)
  //                    .limit(position + RamcastConfig.SIZE_TIMESTAMP))
  //            .slice();
  //    getBuffer().clear();
  //    return ret;
  //  }

  public boolean isFulfilled(RamcastMessage message) {
    int nodeOffset = getNodeOffset(message.getSource());
    for (int groupIndex = 0; groupIndex < message.getGroupCount(); groupIndex++) {
      int position =
              nodeOffset
                      + getSlotOffset(message.getGroupSlot(groupIndex))
                      + groupIndex * RamcastConfig.SIZE_TIMESTAMP;

      if (getBuffer().getInt(position) <= 0 || getBuffer().getInt(position + 4) <= 0) return false;
    }
    return true;
  }

  public int getMaxTimestamp(RamcastMessage message) {
    int max = Integer.MIN_VALUE;
    int nodeOffset = getNodeOffset(message.getSource());
    for (int groupIndex = 0; groupIndex < message.getGroupCount(); groupIndex++) {
      int position =
              nodeOffset
                      + getSlotOffset(message.getGroupSlot(groupIndex))
                      + groupIndex * RamcastConfig.SIZE_TIMESTAMP;
      if (max < getBuffer().getInt(position + 8)) max = getBuffer().getInt(position + 8);
    }
    return max;
  }

  public void freeTimestamp(RamcastMessage message) {
    int nodeOffset = getNodeOffset(message.getSource());
    for (int groupIndex = 0; groupIndex < message.getGroupCount(); groupIndex++) {
      int position =
              nodeOffset
                      + getSlotOffset(message.getGroupSlot(groupIndex))
                      + groupIndex * RamcastConfig.SIZE_TIMESTAMP;
      if (RamcastConfig.LOG_ENABLED)
        logger.debug(
                "[{}] freeing memory at {} {} {} {}",
                message.getId(),
                position,
                position + 4,
                position + 8,
                (ByteBuffer) getBuffer().clear());
      ((ByteBuffer) getBuffer().clear()).putInt(position, 0);
      ((ByteBuffer) getBuffer().clear()).putInt(position + 4, 0);
      ((ByteBuffer) getBuffer().clear()).putInt(position + 8, 0);
      ((ByteBuffer) getBuffer().clear()).put(position + 12, (byte) 0);
    }
  }

  public void setDelivered(RamcastMessage message) {
    int nodeOffset = getNodeOffset(message.getSource());
    for (int groupIndex = 0; groupIndex < message.getGroupCount(); groupIndex++) {
      int position =
              nodeOffset
                      + getSlotOffset(message.getGroupSlot(groupIndex))
                      + groupIndex * RamcastConfig.SIZE_TIMESTAMP;
      ((ByteBuffer) getBuffer().clear()).put(position + 12, (byte) 1);
    }
  }

  public int readSlotBallot(int slot) {
    return getBuffer().getInt(slot * RamcastConfig.SIZE_TIMESTAMP);
  }

  public int readSlotSequence(int slot) {
    return getBuffer().getInt(slot * RamcastConfig.SIZE_TIMESTAMP + 4);
  }

  public int readSlotValue(int slot) {
    return getBuffer().getInt(slot * RamcastConfig.SIZE_TIMESTAMP + 8);
  }

  public byte readSlotStatus(int slot) {
    return getBuffer().get(slot * RamcastConfig.SIZE_TIMESTAMP + 12);
  }
}
