package Account;

import Shared.DispatcherBase;
import Shared.GlobalConfig;
import Shared.Package;
import as.As;
import ascode.AsCode;
import org.apache.log4j.Logger;

public class AccountEventDispatcher extends DispatcherBase {
		private static AccountEventDispatcher instance = new AccountEventDispatcher();
		private static final Logger logger = Logger.getLogger(AccountEventDispatcher.class);
		private AccountEventDispatcher(){
			try {
				table.put((short) AsCode.OpCode.login_VALUE, Wrapper.newWithMessage(As.Login.PARSER, AccountSession.class,"login"));
				table.put((short) AsCode.OpCode.chooseGameServer_VALUE, Wrapper.newWithMessage(As.ChoseGameServer.PARSER, AccountSession.class,"chooseGameServer"));
				table.put((short) AsCode.OpCode.getServerList_VALUE, Wrapper.newOnlyOpcode(AccountSession.class,"getServerList"));
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		public static AccountEventDispatcher getInstance(){
			return instance;
		}
		
		public boolean call(Package pack, AccountSession s) {
			if(GlobalConfig.debug())
				this.printRequest(pack.opcode, AsCode.OpCode.getDescriptor().findValueByNumber(pack.opcode));
			if(!s.valid()){
				if(pack.opcode != AsCode.OpCode.login_VALUE)
					return false;
			}
			else{
				if(pack.opcode == AsCode.OpCode.login_VALUE)
					return false;
			}
			ParseResult o;
			try {
				o = parseMessage(pack);
			} catch (Exception e) {
				return false;
			}
			return invoke(o, pack.opcode, s);
		}
}
