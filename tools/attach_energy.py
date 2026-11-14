"""Install hooks into the decoded 5.0.8 energy service before assembling classes7.dex."""
from pathlib import Path
import re
path=Path('apk/smali_classes7/com/zeekrlife/vehicle/one/api/module/energy/GLEnergyServiceImpl.smali')
parts=path.read_text().split('\n.method ')
expected={'startCharge','stopCharge','startDischarge','stopDischarge','batteryTempRegulation',
          'lowVoltageBatteryProtection','setChargeSoc','setDischargeSoc','setSingleChargingPlan',
          'setCycleChargingPlan','setChargingPlan','setBookingCharge','setTravelPlan','setTravelPlanPlus'}
found=set()
for i,method in enumerate(parts[1:],1):
    h=re.match(r'public (\w+)\((.*?)\)Lkotlinx/coroutines/flow/Flow;',method)
    if not h or h[1] not in expected:continue
    name,args=h.groups();found.add(name)
    if 'Lcom/lenid/accountlab/EnergyCommand;' in method:continue
    args=re.sub('L(?!java/lang/String;)[^;]+;', 'Ljava/lang/Object;',args)
    n=len(re.findall('L[^;]+;|Z',args))
    registers=('invoke-static/range {p1 .. p%d}' % n) if n else 'invoke-static {}'
    directive=method.index('.end annotation')+len('.end annotation')
    assert re.search(r'\.locals [1-9]',method)
    parts[i]=method[:directive]+f'\n    {registers}, Lcom/lenid/accountlab/EnergyCommand;->{name}({args})V\n    invoke-static {{}}, Lkotlinx/coroutines/flow/FlowKt;->emptyFlow()Lkotlinx/coroutines/flow/Flow;\n    move-result-object v0\n    return-object v0\n'+method[directive:]
assert found==expected,expected-found
path.write_text('\n.method '.join(parts))
