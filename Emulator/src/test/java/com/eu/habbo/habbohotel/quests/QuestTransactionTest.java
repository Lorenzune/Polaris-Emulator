package com.eu.habbo.habbohotel.quests;
import com.eu.habbo.Emulator;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.economy.*;
import com.eu.habbo.habbohotel.items.*;
import com.eu.habbo.habbohotel.users.Habbo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class QuestTransactionTest {
    @ParameterizedTest @CsvSource({"currency,false","item,false","badge,false","currency,true","item,true","badge,true"})
    void rewardAndProgressShareOneCommitAndRollbackTogether(String kind,boolean fail) throws Exception {
        var ctor=QuestManager.class.getDeclaredConstructor();ctor.setAccessible(true);var manager=ctor.newInstance();
        var d=new QuestDefinition();d.code="welcome";d.title="Welcome";d.reward.kind=kind;d.reward.itemCode="chair";d.reward.badge="TEST";
        for(String field:List.of("definitions","loaded")){var f=QuestManager.class.getDeclaredField(field);f.setAccessible(true);f.set(manager,field.equals("definitions")?Map.of(d.code,d):true);}
        var user=mock(Habbo.class,RETURNS_DEEP_STUBS);when(user.getHabboInfo().getId()).thenReturn(7);when(user.getHabboInfo().getCurrentRoom()).thenReturn(null);
        var database=mock(Database.class,RETURNS_DEEP_STUBS);var environment=mock(GameEnvironment.class,RETURNS_DEEP_STUBS);
        var base=mock(Item.class);when(base.getId()).thenReturn(42);when(environment.getItemManager().getItem("chair")).thenReturn(base);
        var c=mock(Connection.class);when(database.getDataSource().getConnection()).thenReturn(c);
        var progressStatement=mock(PreparedStatement.class);
        when(c.prepareStatement(anyString())).thenAnswer(invocation->{
            String sql=invocation.getArgument(0);if(sql.startsWith("INSERT INTO wired_quest_progress"))return progressStatement;
            var s=mock(PreparedStatement.class);var rs=mock(ResultSet.class);when(s.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(sql.startsWith("SELECT id FROM users WHERE"));return s;
        });
        var itemStatement=mock(PreparedStatement.class);var keys=mock(ResultSet.class);
        when(c.prepareStatement(anyString(),eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(itemStatement);when(itemStatement.getGeneratedKeys()).thenReturn(keys);when(keys.next()).thenReturn(true);when(keys.getInt(1)).thenReturn(90);
        if(fail)when(progressStatement.executeUpdate()).thenThrow(new SQLException("Simulated persistence failure"));
        var method=QuestManager.class.getDeclaredMethod("mutateLocked",Habbo.class,String.class,String.class,int.class,boolean.class);method.setAccessible(true);
        try(var emulator=mockStatic(Emulator.class);var ledger=mockStatic(EconomyLedger.class);var wallet=mockStatic(com.eu.habbo.habbohotel.users.LedgerWalletMutation.class)){
            emulator.when(Emulator::getDatabase).thenReturn(database);emulator.when(Emulator::getGameEnvironment).thenReturn(environment);emulator.when(Emulator::getIntUnixTimestamp).thenReturn(100);
            ledger.when(()->EconomyLedger.apply(eq(c),any(EconomyOperation.class))).thenReturn(new EconomyMutationResult(0,1,true));
            if(fail){var thrown=assertThrows(InvocationTargetException.class,()->method.invoke(manager,user,d.code,"give",1,false));assertInstanceOf(SQLException.class,thrown.getCause());verify(c).rollback();verify(c,never()).commit();}
            else {assertEquals(true,method.invoke(manager,user,d.code,"give",1,false));verify(c).commit();verify(c,never()).rollback();assertTrue(manager.progress(7,d.code).rewarded);}
            verify(c).setAutoCommit(false);verify(progressStatement).setString(eq(2),contains("\"rewarded\":true"));
            if(kind.equals("currency"))ledger.verify(()->EconomyLedger.apply(eq(c),any(EconomyOperation.class)));
            if(kind.equals("item"))verify(itemStatement).executeUpdate();
            if(kind.equals("badge"))verify(c).prepareStatement("INSERT INTO users_badges(user_id,badge_code,slot_id) VALUES(?,?,0)");
        }
    }
}
