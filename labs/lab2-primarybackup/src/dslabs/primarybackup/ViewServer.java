package dslabs.primarybackup;

import static dslabs.primarybackup.PingCheckTimer.PING_CHECK_MILLIS;

import dslabs.framework.Address;
import dslabs.framework.Node;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class ViewServer extends Node {
  static final int STARTUP_VIEWNUM = 0;
  private static final int INITIAL_VIEWNUM = 1;

  // Your code here...
  private View currentView;
  private boolean primaryAcked;
  private Set<Address> pingThisInterval;
  private Set<Address> pingPrevInterval;

  /* -----------------------------------------------------------------------------------------------
   *  Construction and Initialization
   * ---------------------------------------------------------------------------------------------*/
  public ViewServer(Address address) {
    super(address);
  }

  @Override
  public void init() {
    set(new PingCheckTimer(), PING_CHECK_MILLIS);
    // Your code here...
    currentView = new View(STARTUP_VIEWNUM, null, null);
    primaryAcked = true;
    pingThisInterval = new HashSet<>();
    pingPrevInterval = new HashSet<>();
  }

  /* -----------------------------------------------------------------------------------------------
   *  Message Handlers
   * ---------------------------------------------------------------------------------------------*/
  private void handlePing(Ping m, Address sender) {
    // Your code here...
    pingThisInterval.add(sender);
    if(sender.equals(currentView.primary()) && m.viewNum() == currentView.viewNum()) {
      primaryAcked = true;
    }
    if(currentView.viewNum() == STARTUP_VIEWNUM) {
      //call view init helper
      viewInit(sender);
    } else {
      tryChangeView();
    }
    send(new ViewReply(currentView), sender);
  }

  private void handleGetView(GetView m, Address sender) {
    // Your code here...
    send(new ViewReply(currentView), sender);
  }

  /* -----------------------------------------------------------------------------------------------
   *  Timer Handlers
   * ---------------------------------------------------------------------------------------------*/
  private void onPingCheckTimer(PingCheckTimer t) {
    // Your code here...
    pingPrevInterval = pingThisInterval;
    pingThisInterval = new HashSet<>();
    // update view if needed(primary died or backup died)
    tryChangeView();
    set(t, PING_CHECK_MILLIS);
  }

  /* -----------------------------------------------------------------------------------------------
   *  Utils
   * ---------------------------------------------------------------------------------------------*/
  // Your code here...
  private boolean isAlive(Address address) {
    return pingPrevInterval.contains(address) || pingThisInterval.contains(address);
  }
  private Address findIdle() {
    for(Address a : pingThisInterval) {
      if(!a.equals(currentView.primary()) && !a.equals(currentView.backup())) {
        return a;
      }
    }
    for(Address a : pingPrevInterval) {
      if(!a.equals(currentView.primary()) && !a.equals(currentView.backup())) {
        return a;
      }
    }
    return null;
  }
  private void tryChangeView() {
    // stucked
    if(!primaryAcked) {
      return;
    }
    Address newPrimary = currentView.primary();
    Address newBackup = currentView.backup();
    if(newBackup == null){
      newBackup = findIdle();
    }
    else if(!isAlive(currentView.backup())){
      newBackup = findIdle();
    }
    else if(!isAlive(currentView.primary())){
        newPrimary = currentView.backup();
        newBackup = findIdle();
    } 
    if(newPrimary != currentView.primary() || newBackup != currentView.backup()) {
      currentView = new View(currentView.viewNum() + 1, newPrimary, newBackup);
      primaryAcked = false;
    }
  }
  private void viewInit(Address sender) {
    currentView = new View(INITIAL_VIEWNUM, sender, null);
    primaryAcked = false;
  }
}
