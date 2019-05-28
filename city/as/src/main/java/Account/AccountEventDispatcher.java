package Account;

import Shared.DispatcherBase;
import Shared.GlobalConfig;
import Shared.Package;
import as.As;
import ascode.AsCode;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AccountEventDispatcher extends DispatcherBase {
	private static Set<Integer> opcodeSet = new HashSet<>();
	private static AccountEventDispatcher instance = new AccountEventDispatcher();
	private static final Logger logger = Logger.getLogger(AccountEventDispatcher.class);


	private AccountEventDispatcher()
	{
		try
		{
			table.put((short) AsCode.OpCode.login_VALUE, Wrapper.newWithMessage(As.Login.PARSER, AccountSession.class, "login"));
			table.put((short) AsCode.OpCode.chooseGameServer_VALUE, Wrapper.newWithMessage(As.ChoseGameServer.PARSER, AccountSession.class, "chooseGameServer"));
			table.put((short) AsCode.OpCode.getServerList_VALUE, Wrapper.newOnlyOpcode(AccountSession.class, "getServerList"));
			table.put((short) AsCode.OpCode.verificationInvitationCode_VALUE, Wrapper.newWithMessage(As.String.PARSER, AccountSession.class, "verificationInvitationCode"));
			table.put((short) AsCode.OpCode.getAuthCode_VALUE, Wrapper.newWithMessage(As.String.PARSER, AccountSession.class, "getAuthCode"));
			table.put((short) AsCode.OpCode.createAccount_VALUE, Wrapper.newWithMessage(As.RegistAccount.PARSER, AccountSession.class, "createAccount"));
			table.put((short) AsCode.OpCode.modifyPwdVerify_VALUE, Wrapper.newWithMessage(As.VerifyInfo.PARSER, AccountSession.class, "modifyPwdVerify"));
			table.put((short) AsCode.OpCode.cancleModefyPwd_VALUE, Wrapper.newOnlyOpcode(AccountSession.class, "cancleModefyPwd"));
			table.put((short) AsCode.OpCode.modifyPwd_VALUE, Wrapper.newWithMessage(As.String.PARSER, AccountSession.class, "modifyPwd"));
			opcodeSet.addAll(Arrays.asList(AsCode.OpCode.login_VALUE,
					AsCode.OpCode.verificationInvitationCode_VALUE,
					AsCode.OpCode.getAuthCode_VALUE,
					AsCode.OpCode.createAccount_VALUE,
					AsCode.OpCode.modifyPwdVerify_VALUE,
					AsCode.OpCode.cancleModefyPwd_VALUE,
					AsCode.OpCode.modifyPwd_VALUE));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}



	public static AccountEventDispatcher getInstance()
	{

		return instance;
	}

	public boolean call(Package pack, AccountSession s)
	{

		if (GlobalConfig.debug())
			this.printRequest(pack.opcode, AsCode.OpCode.getDescriptor().findValueByNumber(pack.opcode));
		if (!s.valid())
		{
			if (!opcodeSet.contains((int)pack.opcode))
				return false;
		}
		else
		{
			if (opcodeSet.contains((int)pack.opcode))
				return false;
		}
		ParseResult o;
		try
		{
			o = parseMessage(pack);
		}
		catch (Exception e)
		{
			return false;
		}
		return invoke(o, pack.opcode, s);
	}
}
