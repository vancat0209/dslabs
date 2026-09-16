package dslabs.primarybackup;

import java.util.Objects;

import dslabs.atmostonce.AMOApplication;
import dslabs.atmostonce.AMOResult;
import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Node;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class PBServer extends Node {
  private final Address viewServer;

  // Your code here...
  private AMOApplication<Application> app;
  private View view;
  private boolean synced = true;

  private Address pendingClient;
  private Request pendingRequest;

  /* -----------------------------------------------------------------------------------------------
   *  Construction and Initialization
   * ---------------------------------------------------------------------------------------------*/
  PBServer(Address address, Address viewServer, Application app) {
    super(address);
    this.viewServer = viewServer;

    // Your code here...
    this.app = new AMOApplication<>(app);
  }

  @Override
  public void init() {
    // Your code here...
    send(new Ping(ViewServer.STARTUP_VIEWNUM), viewServer);
    set(new PingTimer(), PingTimer.PING_MILLIS);
  }

  /* -----------------------------------------------------------------------------------------------
   *  Message Handlers
   * ---------------------------------------------------------------------------------------------*/
  private void handleRequest(Request m, Address sender) {
    // Your code here...
    if(!isPrimary()) {
      return;
    }
    // wait until backup is synced, the client will retry
    if(view.backup() != null && !synced) {
      return;
    }
    if(pendingClient != null){
      return;
    }
    if(view.backup() == null) {
      AMOResult res = app.execute(m.command());
      send(new Reply(res), sender);
      return;
    }

    // have backup, forward first, wait for forward reply
    pendingClient = sender;
    pendingRequest = m;
    send(new Forward(m.command()), view.backup());
    set(new ForwardTimer(m.command(), view.viewNum()), ForwardTimer.FORWARD_MILLIS);
  }

  private void handleForward(Forward m, Address sender) {
    if(isPrimary()) {
      send (new ForwardReply(false, null), sender);
      return;
    }
    if(!isBackup() || !sender.equals(view.primary())) {
      send(new ForwardReply(false, null), sender);
      return;
    }
    AMOResult res = app.execute(m.command());
    send(new ForwardReply(true, res), sender);
  }

  private void handleForwardReply(ForwardReply m, Address sender) {
    if(pendingClient == null|| pendingRequest == null) {
      return;
    }
    if(!isPrimary() || !m.success() || !sender.equals(view.backup()) || !synced) {
      pendingClient = null;
      pendingRequest = null;
      return;
    }
    // backup ok, execute on primary
    AMOResult res = app.execute(pendingRequest.command());
    send(new Reply(res), pendingClient);
    pendingClient = null;
    pendingRequest = null;
  }

  private void handleViewReply(ViewReply m, Address sender) {
    // Your code here...
    View old = this.view;
    if(old != null && m.view().viewNum() <= old.viewNum()){
      return;
    }
    this.view = m.view();
    
    // on view change, clear the pending state otherwise server will stuck, the client can't retry cause
    // pending != null, yet when forward timer fires, it can do nothing cause the stale pending request 
    // doesn't match the new view.
    pendingClient = null;
    pendingRequest = null;
    // either init or it was the backup||primary
    if(isPrimary()){
      if(view.backup() == null) {
        synced = true;
      } else if(old == null || old.backup() == null || !Objects.equals(old.backup(), view.backup())) {
        synced = false;
        send(new StateTransfer(app, view.viewNum()), view.backup());
        set(new StateTransferTimer(view.backup(), view.viewNum()), StateTransferTimer.STATE_TRANSFER_MILLIS);
      }
    } else { // idle or backup, clean the state
      synced = true;
    }
  }

  // Your code here...

  /* -----------------------------------------------------------------------------------------------
   *  Timer Handlers
   * ---------------------------------------------------------------------------------------------*/
  private void onPingTimer(PingTimer t) {
    // Your code here...
    send(new Ping(pingViewNum()), viewServer);
    set(t, PingTimer.PING_MILLIS);
  }

  // Your code here...
  private void onStateTransferTimer(StateTransferTimer t) {
    if(synced || !isPrimary()) {
      return;
    }
    if(view.backup() != null && view.backup().equals(t.backup()) && view.viewNum() == t.viewNum()) {
      send(new StateTransfer(app, view.viewNum()), view.backup());
      set(t, StateTransferTimer.STATE_TRANSFER_MILLIS);
    }
  }
  // can't just let the client retry, too slow causing timeout, need to retransmit.
  private void onForwardTimer(ForwardTimer t) {
    if(pendingRequest != null && isPrimary() && synced && view != null && view.viewNum() == t.viewNum()
      && pendingRequest.command().equals(t.command()))  {
      send(new Forward(pendingRequest.command()), view.backup());
      set(t, ForwardTimer.FORWARD_MILLIS);
    }
  }

  /* -----------------------------------------------------------------------------------------------
   *  Utils
   * ---------------------------------------------------------------------------------------------*/
  // Your code here...
  private int pingViewNum() {
    if(view == null) {
      return ViewServer.STARTUP_VIEWNUM;
    }
    // primary and backup is still syncing
    if (isPrimary() && view.backup() != null && !synced) {
      return view.viewNum()-1;
    }
    return view.viewNum();
  }

  private boolean isPrimary() {
    return view !=null && address().equals(view.primary());
  }

  private boolean isBackup() {
    return view !=null && address().equals(view.backup());
  }

  //backup receive state transfer from primary
  private void handleStateTransfer(StateTransfer m, Address sender) {
    this.app = m.app();
    send(new StateTransferReply(m.viewNum()), sender);
  }

  //primary receive state transfer reply from backup
  private void handleStateTransferReply(StateTransferReply m, Address sender) {
    if(isPrimary() && m.viewNum() == view.viewNum() && sender.equals(view.backup())) {
      synced = true;
    }
  }

}
