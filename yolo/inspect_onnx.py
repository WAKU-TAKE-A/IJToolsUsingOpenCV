import onnx

def inspect_onnx(model_path):
    print(f"Inspecting ONNX model: {model_path}")
    model = onnx.load(model_path)
    
    print("\n[Inputs]")
    for input in model.graph.input:
        name = input.name
        shape = [dim.dim_value if dim.HasField("dim_value") else dim.dim_param for dim in input.type.tensor_type.shape.dim]
        print(f"  Name: {name}, Shape: {shape}, Type: {input.type.tensor_type.elem_type}")
        
    print("\n[Outputs]")
    for output in model.graph.output:
        name = output.name
        shape = [dim.dim_value if dim.HasField("dim_value") else dim.dim_param for dim in output.type.tensor_type.shape.dim]
        print(f"  Name: {name}, Shape: {shape}, Type: {output.type.tensor_type.elem_type}")

if __name__ == "__main__":
    inspect_onnx("yolo26s-cls.onnx")
