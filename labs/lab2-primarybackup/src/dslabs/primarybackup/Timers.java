package dslabs.primarybackup;

import dslabs.atmostonce.AMOCommand;
import dslabs.framework.Address;
import dslabs.framework.Timer;
import lombok.Data;

@Data
final class PingCheckTimer implements Timer {
  static final int PING_CHECK_MILLIS = 100;
}

@Data
final class PingTimer implements Timer {
  static final int PING_MILLIS = 25;
}

@Data
final class ClientTimer implements Timer {
  static final int CLIENT_RETRY_MILLIS = 100;

  // Your code here...
  private final int sequenceNum;
}

// Your code here...
@Data
final class StateTransferTimer implements Timer {
  static final int STATE_TRANSFER_MILLIS = 100;
  private final Address backup;
  private final int viewNum;
}

@Data
final class ForwardTimer implements Timer {
  static final int FORWARD_MILLIS = 25;
  private final AMOCommand command;
  private final int viewNum;
}
