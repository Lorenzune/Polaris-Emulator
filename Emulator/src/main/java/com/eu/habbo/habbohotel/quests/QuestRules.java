package com.eu.habbo.habbohotel.quests;

import java.util.*;

/** Pure transition rules shared by Wired mutations and the quest panel. */
public final class QuestRules {
    public record Change(Map<String,QuestProgress> progress, List<QuestDefinition> completed, boolean changed) {}
    public static List<QuestDefinition> members(QuestDefinition chain, Collection<QuestDefinition> definitions) {
        return definitions.stream().filter(d -> !d.chain && d.enabled && d.chainCode.equals(chain.code))
                .sorted(Comparator.comparingInt((QuestDefinition d)->d.position).thenComparing(d->d.code)).toList();
    }
    public static int target(QuestDefinition d, Collection<QuestDefinition> definitions) { return d.chain ? members(d,definitions).size() : d.target; }
    public static boolean available(QuestDefinition d, Collection<QuestDefinition> definitions, long now) {
        if (!d.available(now)) return false;
        return d.chainCode.isEmpty() || definitions.stream().anyMatch(parent -> parent.chain && parent.code.equals(d.chainCode) && parent.available(now));
    }
    public static Change apply(Collection<QuestDefinition> definitions, Map<String,QuestProgress> current, String code, String action, int value, boolean override, int now) {
        var d=definitions.stream().filter(q->q.code.equals(code)&&available(q,definitions,now)).findFirst().orElseThrow(()->new IllegalArgumentException("Missione inesistente o disattivata"));
        Map<String,QuestProgress> next=new LinkedHashMap<>(); current.forEach((k,p)->next.put(k,p.copy()));
        var p=next.computeIfAbsent(code,k->new QuestProgress());
        if (action.equals("give") && p.active && !override) return new Change(next,List.of(),false);
        if (action.equals("change") && !p.active) return new Change(next,List.of(),false);
        if (!List.of("give","change","remove").contains(action)) throw new IllegalArgumentException("Operazione missione non valida");
        if (!d.chain && !d.chainCode.isEmpty() && !action.equals("remove")) {
            var chain=definitions.stream().filter(q->q.chain && q.available(now) && q.code.equals(d.chainCode)).findFirst().orElseThrow(()->new IllegalArgumentException("Catena non disponibile"));
            for(var member:members(chain,definitions)) { if(member.code.equals(code)) break; var prior=next.get(member.code); if(prior==null || !prior.active || prior.value<member.target) return new Change(next,List.of(),false); }
        }
        boolean oldActive=p.active; int oldValue=p.value;
        int goal=target(d,definitions); if(goal<1) throw new IllegalArgumentException("La catena non ha missioni");
        p.active=!action.equals("remove"); p.value=p.active?Math.max(0,Math.min(goal,value)):0;
        if(p.createdAt==0 && p.active) p.createdAt=now; p.updatedAt=now;
        if(d.chain && members(d,definitions).stream().anyMatch(member -> !member.available(now)) && !action.equals("remove")) throw new IllegalArgumentException("La catena contiene missioni scadute");
        if(d.chain) {
            var members=members(d,definitions);
            for(int i=0;i<members.size();i++) { var member=members.get(i); var m=next.computeIfAbsent(member.code,k->new QuestProgress());
                m.active=p.active && i<=p.value; m.value=p.active&&i<p.value?member.target:0;
                if(m.createdAt==0&&m.active)m.createdAt=now; m.updatedAt=now;
            }
        } else if(!d.chainCode.isEmpty()) {
            var chain=definitions.stream().filter(q->q.chain&&q.available(now)&&q.code.equals(d.chainCode)).findFirst().orElse(null);
            if(chain!=null) {
                var cp=next.computeIfAbsent(chain.code,k->new QuestProgress()); var members=members(chain,definitions);
                cp.active=members.stream().anyMatch(m->next.get(m.code)!=null&&next.get(m.code).active);
                cp.value=(int)members.stream().filter(m->next.get(m.code)!=null&&next.get(m.code).active&&next.get(m.code).value>=m.target).count();
                if(cp.createdAt==0&&cp.active)cp.createdAt=now;cp.updatedAt=now;
                if(p.active&&p.value>=d.target) { int i=members.indexOf(d); if(i+1<members.size() && members.get(i+1).available(now)) { var following=next.computeIfAbsent(members.get(i+1).code,k->new QuestProgress()); if(!following.active) {following.active=true;following.createdAt=now;following.updatedAt=now;} } }
            }
        }
        List<QuestDefinition> completed=new ArrayList<>();
        for(var q:definitions) {var state=next.get(q.code);int t=target(q,definitions);if(available(q,definitions,now)&&state!=null&&state.active&&t>0&&state.value>=t&&!state.rewarded) {state.rewarded=true;completed.add(q);} }
        return new Change(next,completed,oldActive!=p.active || oldValue!=p.value || !completed.isEmpty());
    }
}
