from pathlib import Path
import re
root=Path('apk/smali_classes7/com/zeekrlife/vehicle')
dispatcher=root/'manager/VehicleActionDispatcher.smali'
env=root/'one/api/module/env/GLEnvConditionServiceImpl.smali'
sink='Lcom/lenid/accountlab/ClimateCommand;->'
end='    invoke-static {}, Lkotlinx/coroutines/flow/FlowKt;->emptyFlow()Lkotlinx/coroutines/flow/Flow;\n    move-result-object v0\n    return-object v0\n'
hooks={'sendAirConditionCmd(ZIF':('air',7), 'sendSeatHeatOrWindCmd(':('seats',10),
       'sendSteerWheelCmd(':('wheel',4),'sendCabinVentilationCMD(':('ventilation',5),
       'sendFragranceCmd(':('fragrance',6),'sendOxygenCMD(':('oxygen',3),'setAiClimate(':('intelligent',1)}
used=set()
for f in (dispatcher,env):
 parts=f.read_text().split('\n.method ')
 for index,method in enumerate(parts[1:],1):
  header=method.splitlines()[0]
  if not header.startswith('public '):continue
  name=header.split()[-1]
  insert=''
  for prefix,(target,count) in hooks.items():
   if not name.startswith(prefix) or (f==env)!=(prefix=='setAiClimate('):continue
   sig=name.split('(',1)[1].split(')',1)[0].split('Lkotlin/coroutines/Continuation;')[0]
   sig=re.sub(r'L(?!java/lang/String;)[^;]+;', 'Ljava/lang/Object;',sig)
   insert=f'    invoke-static/range {{p1 .. p{count}}}, {sink}{target}({sig})V\n'+end
   assert prefix not in used
   used.add(prefix)
  if name.startswith(('sendDefrostOnCMD(','sendOneSdkControl(','sendControl(')):
   spec='p4' if name.startswith('sendDefrost') else ('p5' if name.startswith('sendOne') else None)
   insert='    move-object/from16 v0, p1\n'+('    move-object/from16 v1, '+spec+'\n' if spec else '    const/4 v1, 0x0\n')+'    invoke-static {v0, v1}, '+sink+'simple(Ljava/lang/Object;Ljava/lang/Object;)Z\n    move-result v0\n    if-eqz v0, :stock_non_climate\n'+end+'    :stock_non_climate\n'
  if not insert:continue
  if sink in method:continue
  # Keep parameter register indices stable for original code.
  locals_n=int(re.search(r'\.locals (\d+)',method)[1]);
  if locals_n==1 and ':stock_non_climate' in insert:method=method.replace('.locals 1','.locals 2',1)
  off=method.index('\n',method.index('.end annotation')+len('.end annotation'))+1 if '.end annotation' in method else method.index('\n',method.index('.locals '))+1
  parts[index]=method[:off]+'\n'+insert+'\n'+method[off:]
 f.write_text('\n.method '.join(parts))
assert used==hooks.keys(),used
