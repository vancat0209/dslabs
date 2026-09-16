package dslabs.primarybackup;

import dslabs.atmostonce.AMOApplication;
import dslabs.atmostonce.AMOCommand;
import dslabs.atmostonce.AMOResult;
import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Message;
import lombok.Data;

/* -----------------------------------------------------------------------------------------------
 *  ViewServer Messages
 * ---------------------------------------------------------------------------------------------*/
@Data
class Ping implements Message {
  private final int viewNum;
}

@Data
class GetView implements Message {}

@Data
class ViewReply implements Message {
  private final View view;
}

/* -----------------------------------------------------------------------------------------------
 *  Primary-Backup Messages
 * ---------------------------------------------------------------------------------------------*/
@Data
class Request implements Message {
  // Your code here...
  private final AMOCommand command;
}

@Data
class Reply implements Message {
  // Your code here...
  private final AMOResult result;
}

// Your code here...
@Data 
class Forward implements Message {
  private final AMOCommand command;
}

@Data
class ForwardReply implements Message {
  private final boolean success;
  private final AMOResult result;
  // Client address + sequenceNum uniquely identifies which forwarded request this replies to
  private final Address clientAddress;
  private final int sequenceNum;
}

@Data 
class StateTransfer implements Message {
  private final AMOApplication<Application> app;
  private final int viewNum;
}

@Data
class StateTransferReply implements Message {
  private final int viewNum;
}