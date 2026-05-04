import onnx

def extract_names(model_path, output_path):
    model = onnx.load(model_path)
    # Ultralytics stores names in metadata_props
    names = None
    for prop in model.metadata_props:
        if prop.key == 'names':
            names = prop.value
            break
    
    if names:
        # names is usually a string representation of a dict: "{0: 'person', 1: 'bicycle', ...}"
        import ast
        try:
            names_dict = ast.literal_eval(names)
            with open(output_path, 'w', encoding='utf-8') as f:
                for i in range(len(names_dict)):
                    f.write(f"{names_dict[i]}\n")
            print(f"Successfully extracted {len(names_dict)} names to {output_path}")
        except Exception as e:
            print(f"Error parsing names metadata: {e}")
            print(f"Raw metadata: {names}")
    else:
        print("No 'names' metadata found in the ONNX model.")

if __name__ == "__main__":
    extract_names("yolo26s-cls.onnx", "yolo26s-cls.txt")
