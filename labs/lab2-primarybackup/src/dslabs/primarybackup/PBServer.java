package dslabs.primarybackup;

import java.util.Objects;

import dslabs.atmostonce.AMOApplication;
import dslabs.atmostonce.AMOCommand;
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
    set(new ForwardTimer(view.backup()), ForwardTimer.FORWARD_MILLIS);
  }

  private void handleForward(Forward m, Address sender) {
    if(isPrimary()) {
      send (new ForwardReply(false, null, m.command().address(), m.command().sequenceNum()), sender);
      return;
    }
    if(!isBackup() || !sender.equals(view.primary())) {
      send(new ForwardReply(false, null, m.command().address(), m.command().sequenceNum()), sender);
      return;
    }
    AMOResult res = app.execute(m.command());
    send(new ForwardReply(true, res, m.command().address(), m.command().sequenceNum()), sender);
  }

  private void handleForwardReply(ForwardReply m, Address sender) {
    if(pendingClient == null|| pendingRequest == null) {
      return;
    }
    // Verify this reply is for the CURRENT pending request (not a stale one)
    AMOCommand pendingCmd = pendingRequest.command();
    if (!m.clientAddress().equals(pendingCmd.address()) || m.sequenceNum() != pendingCmd.sequenceNum()) {
      // Stale reply from a previous forward - ignore it
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
    this.view = m.view();

    // either init or it was the backup||primary
    if(isPrimary()){
      if(view.backup() == null) {
        synced = true;
        // Clear pending if backup is gone - let client retry
        pendingClient = null;
        pendingRequest = null;
      } else if(old == null || old.backup() == null || !Objects.equals(old.backup(), view.backup())) {
        synced = false;
        // Backup changed - clear pending so client will retry with new backup
        pendingClient = null;
        pendingRequest = null;
        send(new StateTransfer(app, view.viewNum()), view.backup());
        set(new StateTransferTimer(view.backup(), view.viewNum()), StateTransferTimer.STATE_TRANSFER_MILLIS);
      }
    } else { // idle or backup, clean the state
      synced = true;
      pendingClient = null;
      pendingRequest = null;
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
  // just let the client retry
  private void onForwardTimer(ForwardTimer t) {
    // Only clear pending if timer is for current backup
    if (!isPrimary() || view.backup() == null || !t.backup().equals(view.backup())) {
      return;
    }
    pendingClient = null;
    pendingRequest = null;
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
    // Only accept state from current primary
    if (!isBackup() || view == null || !sender.equals(view.primary())) {
      return;
    }
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
