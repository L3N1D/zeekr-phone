"""Connect factory map, refrigeration and tire screens in the decoded 5.0.8 client."""
from pathlib import Path
import re

base = Path("apk/smali_classes7/com/zeekrlife/vehicle")
bridge = "Lcom/lenid/accountlab/"
destination = "Lcom/zeekrlife/vehicle/bean/VehicleDestination;"

def insert(file, signature, code):
    path = base / (file + ".smali")
    text = path.read_text()
    start = re.search(r"^\.method [^\n]* " + re.escape(signature), text, re.M)
    if not start:
        raise ValueError(signature)
    end = text.index("\n.end method", start.start())
    body = text[start.start():end]
    if code in body:
        return
    anchor = body.rfind("    .end annotation")
    offset = body.index("\n", anchor if anchor >= 0 else body.index("    .locals"))
    path.write_text(text[:start.start()+offset] + "\n" + code + text[start.start()+offset:])

getters = []
for index, (name, kind) in enumerate([("Name","String"),("Address","String"),("Longitude","Double"),("Latitude","Double")]):
    getters += [f"    invoke-virtual {{p1}}, {destination}->get{name}()Ljava/lang/{kind};", f"    move-result-object v{index}"]
insert("viewmodel/VehicleViewModel", "send2car("+destination, "\n".join(getters + [
    "    move-object v4, p2",
    f"    invoke-static/range {{v0 .. v4}}, {bridge}DestinationCommand;->send(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Double;Ljava/lang/Double;Ljava/lang/Object;)V",
    "    return-void"]))

for file, name, method in [
    ("one/api/module/external/GLExternalServiceImpl", "setBookVtmCmd", "fridgeSchedule"),
    ("one/api/module/vehiclecontrol/GLVehicleControlServiceImpl", "sendCarRefrigeratorCmd", "fridgeControl"),
]:
    insert(file, name + "(", f"    invoke-static/range {{p1 .. p1}}, {bridge}EnergyCommand;->{method}(Ljava/lang/Object;)V\n"
           "    invoke-static {}, Lkotlinx/coroutines/flow/FlowKt;->emptyFlow()Lkotlinx/coroutines/flow/Flow;\n"
           "    move-result-object v0\n    return-object v0")

file = "view/activitys/VehicleStatusActivity"
insert(file, "dataReset()V", f"    invoke-static {{}}, {bridge}AccountGate;->logged()Z\n    move-result v0\n"
       "    if-eqz v0, :factory_status\n    return-void\n    :factory_status")
path = base / (file + ".smali")
text = path.read_text()
for event in ["Resume", "Pause"]:
    if f".method protected on{event}()V" in text:
        continue
    text += f"""
.method protected on{event}()V
    .locals 0
    invoke-super {{p0}}, Lcom/zeekrlife/vehicle/view/activitys/VehicleBaseActivity;->on{event}()V
    invoke-static {{p0}}, {bridge}TireReadings;->{event.lower()}(Landroid/app/Activity;)V
    return-void
.end method
"""
path.write_text(text)

for name, op in [("getElectronicFenceList","list"), ("createElectronicFence","create"),
                 ("updateElectronicFence","update"), ("enableElectronicFence","enable"), ("deleteElectronicFence","delete")]:
    file = "one/api/module/location/GLLocationServiceImpl"
    path = base / (file + ".smali")
    text = path.read_text()
    text = re.sub(r"(\.method public " + name + r"\([^\n]+\n    \.locals )([0-9]+)",
                  lambda m: m[1] + str(max(3, int(m[2]))), text)
    path.write_text(text)
    obj = "const/4 v1, 0x0" if op == "list" else "move-object v1, p1"
    flag = "move v2, p2" if op == "enable" else "const/4 v2, 0x0"
    insert(file, name + "(", f'    const-string v0, "{op}"\n    {obj}\n    {flag}\n'
           f"    invoke-static {{v0, v1, v2}}, {bridge}GeoFences;->flow(Ljava/lang/String;Ljava/lang/Object;Z)Ljava/lang/Object;\n"
           "    move-result-object v0\n    check-cast v0, Lkotlinx/coroutines/flow/Flow;\n    return-object v0")

insert("one/api/module/vehiclecontrol/GLVehicleControlServiceImpl","sendRemoteSwitch(",
       f"    invoke-static/range {{p1 .. p1}}, {bridge}NativeCommand;->parkingComfort(Ljava/lang/Object;)Z\n"
       "    move-result v0\n    if-eqz v0, :stock_remote_switch\n"
       "    invoke-static {}, Lkotlinx/coroutines/flow/FlowKt;->emptyFlow()Lkotlinx/coroutines/flow/Flow;\n"
       "    move-result-object v0\n    return-object v0\n    :stock_remote_switch")
insert("one/api/module/fastctrl/GLFastCtrlServiceImpl","sendSuspensionControl(",
       f"    invoke-static/range {{p1 .. p1}}, {bridge}EnergyCommand;->suspension(I)V\n"
       "    invoke-static {}, Lkotlinx/coroutines/flow/FlowKt;->emptyFlow()Lkotlinx/coroutines/flow/Flow;\n"
       "    move-result-object v0\n    return-object v0")
