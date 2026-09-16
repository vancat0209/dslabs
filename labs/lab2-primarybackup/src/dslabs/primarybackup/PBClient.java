package dslabs.primarybackup;

import dslabs.atmostonce.AMOCommand;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class PBClient extends Node implements Client {
  private final Address viewServer;

  // Your code here...
  private Command command;
  private int sequenceNum = 0;
  private Result result;

  private View view;

  /* -----------------------------------------------------------------------------------------------
   *  Construction and Initialization
   * ---------------------------------------------------------------------------------------------*/
  public PBClient(Address address, Address viewServer) {
    super(address);
    this.viewServer = viewServer;
  }

  @Override
  public synchronized void init() {
    // Your code here...
    send(new GetView(), viewServer);
  }

  /* -----------------------------------------------------------------------------------------------
   *  Client Methods
   * ---------------------------------------------------------------------------------------------*/
  @Override
  public synchronized void sendCommand(Command command) {
    // Your code here...
    sequenceNum++;
    this.command = command;
    this.result = null;
    sendRequestToPrimary();
  }

  @Override
  public synchronized boolean hasResult() {
    // Your code here...
    return result != null;
  }

  @Override
  public synchronized Result getResult() throws InterruptedException {
    // Your code here...
    while (result == null) {
      wait();
    }
    return result;
  }

  private void sendRequestToPrimary() {
    if (view == null || view.primary() == null) {
      send(new GetView(), viewServer);
      return;
    }
    AMOCommand amo = new AMOCommand(command, address(), sequenceNum);
    send(new Request(amo), view.primary());
    set(new ClientTimer(sequenceNum), ClientTimer.CLIENT_RETRY_MILLIS);
  }

  /* -----------------------------------------------------------------------------------------------
   *  Message Handlers
   * ---------------------------------------------------------------------------------------------*/
  private synchronized void handleReply(Reply m, Address sender) {
    // Your code here...
    if (m.result().sequenceNum() == sequenceNum && result == null) {
      result = m.result().result();
      notify();
    }
  }

  private synchronized void handleViewReply(ViewReply m, Address sender) {
    // Your code here...
    this.view = m.view();
    if(command != null && result == null) {
      sendRequestToPrimary();
    }
  }

  // Your code here...

  /* -----------------------------------------------------------------------------------------------
   *  Timer Handlers
   * ---------------------------------------------------------------------------------------------*/
  // only set timer when sending the request. don't duplicate timer.
  private synchronized void onClientTimer(ClientTimer t) {
    // Your code here...
    if(t.sequenceNum() == sequenceNum && result == null) {
      send(new GetView(), viewServer);
      sendRequestToPrimary();
    }
  }
}
