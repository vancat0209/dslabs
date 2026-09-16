package dslabs.atmostonce;

import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Result;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public final class AMOApplication<T extends Application> implements Application {
  @Getter @NonNull private final T application;

  // Your code here...
  private final Map<Address, AMOResult> clientResultMap = new HashMap<>();

  @Override
  public AMOResult execute(Command command) {
    if (!(command instanceof AMOCommand)) {
      throw new IllegalArgumentException();
    }

    AMOCommand amoCommand = (AMOCommand) command;

    // Your code here...
    Address address = amoCommand.address();
    int seq = amoCommand.sequenceNum();

    if (alreadyExecuted(amoCommand)) {
      return clientResultMap.get(address);
    }
    Result raw = application.execute(amoCommand.command());
    AMOResult result = new AMOResult(raw, seq);
    clientResultMap.put(address, result);
    return result;
  }

  public Result executeReadOnly(Command command) {
    if (!command.readOnly()) {
      throw new IllegalArgumentException();
    }

    if (command instanceof AMOCommand) {
      return execute(command);
    }

    return application.execute(command);
  }

  public boolean alreadyExecuted(AMOCommand amoCommand) {
    // Your code here...
    Address address = amoCommand.address();
    int seq = amoCommand.sequenceNum();
    if (clientResultMap.containsKey(address) && clientResultMap.get(address).sequenceNum() >= seq) {
      return true;
    }
    return false;
  }
}
